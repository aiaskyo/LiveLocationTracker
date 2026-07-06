package com.example.livelocationtracker.utils

/**
 * Single source of truth for tunable values and string keys used across
 * multiple layers (service, repository, UI). Centralizing these avoids
 * magic numbers/strings drifting out of sync between files.
 */
object Constants {

    // Notification
    const val LOCATION_CHANNEL_ID = "location_tracking_channel"
    const val LOCATION_NOTIFICATION_ID = 1001

    // Location update cadence, per spec: every 15s OR every 10m of movement,
    // whichever comes first. We sample the Fused Location Provider faster
    // (FASTEST_SAMPLING_INTERVAL_MS) and apply this time/distance gate
    // ourselves in LocationTrackingService, since the FLP's own
    // minUpdateDistanceMeters + interval combination is AND-like rather than
    // OR-like and would not match this requirement exactly.
    const val UPLOAD_INTERVAL_MS = 15_000L
    const val FASTEST_SAMPLING_INTERVAL_MS = 5_000L
    const val MIN_DISPLACEMENT_METERS = 10f

    // Firestore
    const val FIRESTORE_COLLECTION_LOCATIONS = "live_locations"
    const val FIRESTORE_SUBCOLLECTION_HISTORY = "location_history"

    // Service actions
    const val ACTION_START_TRACKING = "com.example.livelocationtracker.action.START_TRACKING"
    const val ACTION_STOP_TRACKING = "com.example.livelocationtracker.action.STOP_TRACKING"

    // SharedPreferences
    const val PREFS_NAME = "live_location_prefs"
    const val PREF_TRACKING_ENABLED = "tracking_enabled"
}
