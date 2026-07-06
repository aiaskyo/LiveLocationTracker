package com.example.livelocationtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.livelocationtracker.utils.Constants
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings

/**
 * Application entry point.
 *
 * Two things are set up here that matter for reliability:
 *  1. Firestore's persistent local cache is explicitly enabled. This is what
 *     lets [com.example.livelocationtracker.data.repository.LocationRepository]
 *     "just work" offline -- writes are applied to the local cache immediately
 *     and are automatically replayed against the server as soon as
 *     connectivity returns, with zero custom retry logic required.
 *  2. The notification channel used by the foreground tracking service is
 *     created up-front, as required on Android 8.0+ before you can post any
 *     notification on that channel.
 */
class LiveLocationApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            }
        }

        createLocationNotificationChannel()
    }

    private fun createLocationNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.LOCATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
