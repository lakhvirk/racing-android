package com.lakh.racing

import android.app.Application
import android.content.Context
import com.lakh.racing.data.RaceRepository
import com.lakh.racing.data.db.RacingDatabase
import com.lakh.racing.data.offline.OfflineMapDownloader
import com.lakh.racing.location.LocationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre

/** Manual dependency container; one per process. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = RacingDatabase.create(context)
    val repository = RaceRepository(context, db.raceDao())
    val downloader = OfflineMapDownloader(context, repository, appScope)
    val locationHub = LocationHub()
}

class RacingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
    }
}
