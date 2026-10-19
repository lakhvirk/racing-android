package com.lakh.racing.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lakh.racing.MainActivity
import com.lakh.racing.R
import com.lakh.racing.RacingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Latest GPS state, shared between the service and the UI. */
class LocationHub {
    internal val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    internal val _gpsEnabled = MutableStateFlow(true)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled
}

/**
 * Foreground service that reads the GPS chip directly through [LocationManager].
 * GPS needs no network or Play Services, so tracking keeps working with no signal.
 */
class LocationService : Service(), LocationListener {

    private val hub get() = (application as RacingApp).container.locationHub
    private lateinit var locationManager: LocationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        hub._gpsEnabled.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { hub._location.value = it }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, this, Looper.getMainLooper(),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        hub._location.value = location
    }

    override fun onProviderEnabled(provider: String) {
        hub._gpsEnabled.value = true
    }

    override fun onProviderDisabled(provider: String) {
        hub._gpsEnabled.value = false
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Race tracking", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_race)
            .setContentTitle("Race in progress")
            .setContentText("Tracking your position with GPS")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "race_tracking"
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL_MS = 1_000L

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            if (hasPermission(context)) {
                ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
