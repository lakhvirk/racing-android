package com.lakh.racing.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lakh.racing.data.model.PoiType
import com.lakh.racing.data.model.Race
import com.lakh.racing.nav.LatLon
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

enum class CameraMode { FOLLOW, NORTH_UP, OVERVIEW }

/**
 * MapLibre map showing the race. All layers are drawn from local data on top of the
 * offline-cached style, so nothing here needs a connection once tiles are downloaded.
 */
@Composable
fun RaceMap(
    race: Race,
    checkedIn: Set<String>,
    position: LatLon?,
    bearing: Float?,
    cameraMode: CameraMode,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView, race.id) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = interactive
                isScrollGesturesEnabled = interactive
                isZoomGesturesEnabled = interactive
                isTiltGesturesEnabled = interactive
                isCompassEnabled = interactive
            }
            m.setStyle(Style.Builder().fromUri(MapConfig.STYLE_URL)) { s ->
                addRaceLayers(s, race)
                map = m
                style = s
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds(race), 80))
            }
        }
        onDispose { }
    }

    LaunchedEffect(style, checkedIn) {
        val s = style ?: return@LaunchedEffect
        (s.getSource(SRC_CHECKPOINTS) as? GeoJsonSource)?.setGeoJson(checkpointFeatures(race, checkedIn))
    }

    LaunchedEffect(style, position) {
        val s = style ?: return@LaunchedEffect
        val src = s.getSource(SRC_ME) as? GeoJsonSource ?: return@LaunchedEffect
        src.setGeoJson(
            if (position == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(position.lon, position.lat))),
        )
    }

    LaunchedEffect(map, cameraMode) {
        if (cameraMode == CameraMode.OVERVIEW) {
            map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds(race), 80), 600)
        }
    }

    LaunchedEffect(map, cameraMode, position, bearing) {
        val m = map ?: return@LaunchedEffect
        val p = position ?: return@LaunchedEffect
        val camera = when (cameraMode) {
            CameraMode.FOLLOW -> CameraPosition.Builder()
                .target(LatLng(p.lat, p.lon)).zoom(16.0).tilt(45.0).bearing((bearing ?: 0f).toDouble()).build()
            CameraMode.NORTH_UP -> CameraPosition.Builder()
                .target(LatLng(p.lat, p.lon)).zoom(15.0).tilt(0.0).bearing(0.0).build()
            CameraMode.OVERVIEW -> return@LaunchedEffect
        }
        m.easeCamera(CameraUpdateFactory.newCameraPosition(camera), 900)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val SRC_ROUTE = "race-route"
private const val SRC_CHECKPOINTS = "race-checkpoints"
private const val SRC_POIS = "race-pois"
private const val SRC_ME = "race-me"
private val FONT = arrayOf("Noto Sans Bold")

private fun bounds(race: Race): LatLngBounds = LatLngBounds.Builder().apply {
    race.routePoints.forEach { include(LatLng(it.lat, it.lon)) }
}.build()

private fun checkpointFeatures(race: Race, checkedIn: Set<String>) = FeatureCollection.fromFeatures(
    race.checkpoints.map { cp ->
        Feature.fromGeometry(Point.fromLngLat(cp.lon, cp.lat)).apply {
            addStringProperty("label", if (cp.order == race.checkpoints.maxOf { it.order }) "F" else cp.order.toString())
            addStringProperty("name", cp.name)
            addBooleanProperty("checked", cp.id in checkedIn)
        }
    },
)

private fun addRaceLayers(style: Style, race: Race) {
    val line = LineString.fromLngLats(race.routePoints.map { Point.fromLngLat(it.lon, it.lat) })
    style.addSource(GeoJsonSource(SRC_ROUTE, Feature.fromGeometry(line)))
    style.addLayer(
        LineLayer("route-casing", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(Color.WHITE),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("route-line", SRC_ROUTE).withProperties(
            PropertyFactory.lineColor(Color.parseColor("#FF3D00")),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )

    style.addSource(
        GeoJsonSource(
            SRC_POIS,
            FeatureCollection.fromFeatures(
                race.pois.map { poi ->
                    Feature.fromGeometry(Point.fromLngLat(poi.lon, poi.lat)).apply {
                        addStringProperty("type", poi.type.name)
                        addStringProperty("name", poi.name)
                    }
                },
            ),
        ),
    )
    style.addLayer(
        CircleLayer("poi-dots", SRC_POIS).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get("type"), Expression.color(Color.parseColor("#9E9E9E")),
                    Expression.stop(PoiType.REPAIR.name, Expression.color(Color.parseColor("#FF9800"))),
                    Expression.stop(PoiType.FUEL.name, Expression.color(Color.parseColor("#2196F3"))),
                    Expression.stop(PoiType.MEDICAL.name, Expression.color(Color.parseColor("#E53935"))),
                    Expression.stop(PoiType.SHOP.name, Expression.color(Color.parseColor("#8E24AA"))),
                ),
            ),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
    style.addLayer(
        SymbolLayer("poi-labels", SRC_POIS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(FONT),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textHaloColor(Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
        ).apply { minZoom = 12f },
    )

    style.addSource(GeoJsonSource(SRC_CHECKPOINTS, checkpointFeatures(race, emptySet())))
    style.addLayer(
        CircleLayer("checkpoint-dots", SRC_CHECKPOINTS).withProperties(
            PropertyFactory.circleRadius(15f),
            PropertyFactory.circleColor(
                Expression.switchCase(
                    Expression.toBool(Expression.get("checked")), Expression.color(Color.parseColor("#2E7D32")),
                    Expression.color(Color.parseColor("#FFC400")),
                ),
            ),
            PropertyFactory.circleStrokeColor(Color.BLACK),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
    style.addLayer(
        SymbolLayer("checkpoint-labels", SRC_CHECKPOINTS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(FONT),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor(Color.BLACK),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )

    style.addSource(GeoJsonSource(SRC_ME, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("me-halo", SRC_ME).withProperties(
            PropertyFactory.circleRadius(18f),
            PropertyFactory.circleColor(Color.parseColor("#2979FF")),
            PropertyFactory.circleOpacity(0.25f),
        ),
    )
    style.addLayer(
        CircleLayer("me-dot", SRC_ME).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(Color.parseColor("#2979FF")),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
