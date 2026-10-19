package com.lakh.racing.data.offline

import android.content.Context
import android.util.Log
import com.lakh.racing.data.RaceRepository
import com.lakh.racing.data.db.OfflineState
import com.lakh.racing.data.model.Race
import com.lakh.racing.map.MapConfig
import com.lakh.racing.nav.LatLon
import com.lakh.racing.nav.RouteMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos

data class DownloadProgress(
    val completedResources: Long = 0,
    val requiredResources: Long = 0,
    val bytes: Long = 0,
    val part: Int = 1,
    val parts: Int = 1,
    /** Set while MapLibre reports a network error; it keeps retrying on its own. */
    val networkProblem: String? = null,
    val error: String? = null,
    val done: Boolean = false,
) {
    val fraction: Float
        get() = if (requiredResources == 0L) 0f else (completedResources.toFloat() / requiredResources).coerceIn(0f, 1f)
}

/**
 * Downloads map tiles for a race so the map renders with no connection. Two regions:
 * a low-zoom overview of the whole area and a high-zoom corridor along the route,
 * which keeps long rallies to a sensible size. Downloads run in the app scope so they
 * survive leaving the screen.
 */
class OfflineMapDownloader(
    context: Context,
    private val repository: RaceRepository,
    private val scope: CoroutineScope,
) {
    private val manager = OfflineManager.getInstance(context)
    private val pixelRatio = context.resources.displayMetrics.density
    private val jobs = mutableMapOf<String, Job>()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress

    fun start(race: Race) {
        if (jobs[race.id]?.isActive == true) return
        jobs[race.id] = scope.launch {
            repository.setOffline(race.id, OfflineState.DOWNLOADING)
            publish(race.id, DownloadProgress())
            try {
                deleteRegions(race.id)
                val defs = definitions(race)
                var bytesSoFar = 0L
                defs.forEachIndexed { index, def ->
                    val region = createRegion(def, race.id)
                    var last: OfflineRegionStatus? = null
                    observe(region).collect { event ->
                        when (event) {
                            is RegionEvent.Status -> {
                                last = event.status
                                publish(
                                    race.id,
                                    DownloadProgress(
                                        completedResources = event.status.completedResourceCount,
                                        requiredResources = event.status.requiredResourceCount,
                                        bytes = bytesSoFar + event.status.completedResourceSize,
                                        part = index + 1,
                                        parts = defs.size,
                                    ),
                                )
                            }
                            is RegionEvent.NetworkProblem -> _progress.update {
                                it + (race.id to (it[race.id] ?: DownloadProgress()).copy(networkProblem = event.message))
                            }
                        }
                    }
                    bytesSoFar += last?.completedResourceSize ?: 0
                }
                repository.setOffline(race.id, OfflineState.READY, bytesSoFar)
                publish(race.id, DownloadProgress(1, 1, bytesSoFar, defs.size, defs.size, done = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                repository.setOffline(race.id, OfflineState.NONE)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Offline download failed", e)
                repository.setOffline(race.id, OfflineState.FAILED)
                publish(race.id, DownloadProgress(error = e.message ?: "Download failed"))
            }
        }
    }

    fun cancel(raceId: String) {
        jobs.remove(raceId)?.cancel()
        _progress.update { it - raceId }
    }

    /** Re-checks the downloaded regions, e.g. after the app was killed mid-download. */
    suspend fun verify(raceId: String): Boolean {
        val regions = regionsFor(raceId)
        if (regions.isEmpty()) return false
        return regions.all { status(it).isComplete }
    }

    suspend fun deleteRegions(raceId: String) {
        for (region in regionsFor(raceId)) {
            suspendCancellableCoroutine { cont ->
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = cont.resume(Unit)
                    override fun onError(error: String) = cont.resume(Unit)
                })
            }
        }
    }

    private fun publish(raceId: String, p: DownloadProgress) = _progress.update { it + (raceId to p) }

    private fun definitions(race: Race): List<OfflineRegionDefinition> {
        val points = race.routePoints
        val all = points + race.checkpoints.map { LatLon(it.lat, it.lon) } + race.pois.map { LatLon(it.lat, it.lon) }
        val bounds = LatLngBounds.from(
            all.maxOf { it.lat } + degLat(OVERVIEW_PAD_M),
            all.maxOf { it.lon } + degLon(OVERVIEW_PAD_M, all.first().lat),
            all.minOf { it.lat } - degLat(OVERVIEW_PAD_M),
            all.minOf { it.lon } - degLon(OVERVIEW_PAD_M, all.first().lat),
        )
        val overview = OfflineTilePyramidRegionDefinition(
            MapConfig.STYLE_URL, bounds, OVERVIEW_MIN_ZOOM, OVERVIEW_MAX_ZOOM, pixelRatio,
        )

        // Corridor: squares sampled along the route, plus squares around every checkpoint and POI.
        val matcher = RouteMatcher(points)
        val squares = mutableListOf<List<List<Point>>>()
        var d = 0.0
        while (d <= matcher.totalLengthM) {
            squares += square(matcher.pointAt(d), CORRIDOR_HALF_M)
            d += CORRIDOR_STEP_M
        }
        squares += square(points.last(), CORRIDOR_HALF_M)
        race.checkpoints.forEach { squares += square(LatLon(it.lat, it.lon), POINT_HALF_M) }
        race.pois.forEach { squares += square(LatLon(it.lat, it.lon), POINT_HALF_M) }
        val corridor = OfflineGeometryRegionDefinition(
            MapConfig.STYLE_URL, MultiPolygon.fromLngLats(squares), CORRIDOR_MIN_ZOOM, CORRIDOR_MAX_ZOOM, pixelRatio,
        )
        return listOf(overview, corridor)
    }

    private fun square(c: LatLon, halfM: Double): List<List<Point>> {
        val dLat = degLat(halfM)
        val dLon = degLon(halfM, c.lat)
        val ring = listOf(
            Point.fromLngLat(c.lon - dLon, c.lat - dLat),
            Point.fromLngLat(c.lon + dLon, c.lat - dLat),
            Point.fromLngLat(c.lon + dLon, c.lat + dLat),
            Point.fromLngLat(c.lon - dLon, c.lat + dLat),
            Point.fromLngLat(c.lon - dLon, c.lat - dLat),
        )
        return listOf(ring)
    }

    private fun degLat(m: Double) = m / 111_195.0
    private fun degLon(m: Double, lat: Double) = m / (111_195.0 * cos(Math.toRadians(lat)))

    private suspend fun createRegion(def: OfflineRegionDefinition, raceId: String): OfflineRegion =
        suspendCancellableCoroutine { cont ->
            manager.createOfflineRegion(def, raceId.encodeToByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                override fun onError(error: String) = cont.resumeWithException(IllegalStateException(error))
            })
        }

    private sealed interface RegionEvent {
        data class Status(val status: OfflineRegionStatus) : RegionEvent
        data class NetworkProblem(val message: String) : RegionEvent
    }

    private fun observe(region: OfflineRegion) = callbackFlow {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                trySend(RegionEvent.Status(status))
                if (status.isComplete) close()
            }

            override fun onError(error: OfflineRegionError) {
                // MapLibre retries network failures itself; surface them without failing.
                Log.w(TAG, "Offline region error: ${error.reason} ${error.message}")
                trySend(RegionEvent.NetworkProblem(error.message))
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                close(IllegalStateException("Tile limit of $limit exceeded"))
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        awaitClose { region.setDownloadState(OfflineRegion.STATE_INACTIVE) }
    }

    private suspend fun regionsFor(raceId: String): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) =
                cont.resume(offlineRegions.orEmpty().filter { it.metadata.decodeToString() == raceId })

            override fun onError(error: String) = cont.resume(emptyList())
        })
    }

    private suspend fun status(region: OfflineRegion): OfflineRegionStatus = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = cont.resume(status!!)
            override fun onError(error: String?) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    private companion object {
        const val TAG = "OfflineMapDownloader"
        const val OVERVIEW_PAD_M = 5_000.0
        const val OVERVIEW_MIN_ZOOM = 5.0
        const val OVERVIEW_MAX_ZOOM = 12.0
        const val CORRIDOR_MIN_ZOOM = 13.0
        const val CORRIDOR_MAX_ZOOM = 16.0
        const val CORRIDOR_HALF_M = 1_000.0
        const val CORRIDOR_STEP_M = 500.0
        const val POINT_HALF_M = 500.0
    }
}
