package com.example.livelocationtracker.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.livelocationtracker.R
import com.example.livelocationtracker.data.model.LocationData
import com.example.livelocationtracker.data.repository.AuthRepository
import com.example.livelocationtracker.data.repository.LocationRepository
import com.example.livelocationtracker.ui.map.MapActivity
import com.example.livelocationtracker.utils.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that continuously pulls fixes from the Fused Location
 * Provider and uploads them to Firestore.
 *
 * Because this runs as a proper foreground service (declared with
 * `foregroundServiceType="location"` in the manifest) it keeps running when
 * the app is minimized or swiped away from Recents -- the process hosting
 * this service is not tied to any Activity's lifecycle. This holds as long
 * as:
 *   - the user has granted "Allow all the time" (ACCESS_BACKGROUND_LOCATION), and
 *   - the user has not force-stopped the app from system settings, and
 *   - the OS itself doesn't need to reclaim resources under extreme memory
 *     pressure (in which case START_STICKY tells the system to recreate the
 *     service once resources free up again).
 *
 * Update cadence: the spec calls for a Firestore write every 15 seconds OR
 * whenever the device has moved more than 10 meters, whichever happens
 * first. The Fused Location Provider's own `minUpdateDistanceMeters` acts as
 * an AND-style gate (both the interval AND the distance must elapse before
 * a callback fires), which does not match an OR requirement. Instead we
 * sample fixes frequently (every [Constants.FASTEST_SAMPLING_INTERVAL_MS])
 * and apply the 15s-or-10m gate ourselves in [handleNewLocation].
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationRepository = LocationRepository()
    private val authRepository = AuthRepository()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var lastUploadedLocation: Location? = null
    private var lastUploadTimeMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) {
                Log.w(TAG, "Location temporarily unavailable (GPS/network signal lost)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_STOP_TRACKING) {
            stopTracking()
            return START_NOT_STICKY
        }

        startForeground(Constants.LOCATION_NOTIFICATION_ID, buildNotification(null))
        requestLocationUpdates()

        // If the system kills this service to reclaim memory, START_STICKY
        // tells it to recreate the service (with a null Intent) as soon as
        // resources allow, so tracking resumes without user intervention.
        return START_STICKY
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission; stopping service")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.FASTEST_SAMPLING_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(Constants.FASTEST_SAMPLING_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun handleNewLocation(location: Location) {
        // Always refresh the ongoing notification with the freshest fix,
        // independent of whether this particular fix gets uploaded.
        updateNotification(location)

        val now = System.currentTimeMillis()
        val elapsedSinceLastUpload = now - lastUploadTimeMs
        val distanceMoved = lastUploadedLocation?.distanceTo(location)

        val shouldUpload = lastUploadedLocation == null ||
            elapsedSinceLastUpload >= Constants.UPLOAD_INTERVAL_MS ||
            (distanceMoved != null && distanceMoved >= Constants.MIN_DISPLACEMENT_METERS)

        if (!shouldUpload) return

        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No authenticated user; cannot upload location")
            return
        }

        lastUploadedLocation = location
        lastUploadTimeMs = now

        val data = LocationData(
            userId = userId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            deviceTimestamp = location.time
        )

        serviceScope.launch {
            locationRepository.updateLiveLocation(data)
        }
    }

    private fun buildNotification(location: Location?): Notification {
        val contentText = if (location != null) {
            getString(
                R.string.notification_tracking_text_with_coords,
                location.latitude,
                location.longitude
            )
        } else {
            getString(R.string.notification_tracking_text)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MapActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = Constants.ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, Constants.LOCATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_location_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_stop_action), stopPendingIntent)
            .build()
    }

    private fun updateNotification(location: Location) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(Constants.LOCATION_NOTIFICATION_ID, buildNotification(location))
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = Constants.ACTION_START_TRACKING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = Constants.ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }
}
