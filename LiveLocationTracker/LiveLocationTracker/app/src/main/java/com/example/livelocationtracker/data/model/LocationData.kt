package com.example.livelocationtracker.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A single location fix, shaped exactly as it is stored in Firestore.
 *
 * All properties have default values and the class has no other
 * constructors, which satisfies Firestore's requirement of a public no-arg
 * constructor for automatic (de)serialization via `toObject()` / `set()`.
 */
data class LocationData(
    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",

    @get:PropertyName("latitude") @set:PropertyName("latitude")
    var latitude: Double = 0.0,

    @get:PropertyName("longitude") @set:PropertyName("longitude")
    var longitude: Double = 0.0,

    @get:PropertyName("accuracy") @set:PropertyName("accuracy")
    var accuracy: Float = 0f,

    @get:PropertyName("speed") @set:PropertyName("speed")
    var speed: Float = 0f,

    @get:PropertyName("bearing") @set:PropertyName("bearing")
    var bearing: Float = 0f,

    // Populated by the server the moment Firestore commits the write. Handy
    // for detecting sync latency and for ordering across multiple devices.
    @ServerTimestamp
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Date? = null,

    // Captured on-device at the moment of the GPS fix (epoch millis). Kept
    // in addition to the server timestamp so the client always has an
    // ordering key, even before the write round-trips to the server.
    @get:PropertyName("deviceTimestamp") @set:PropertyName("deviceTimestamp")
    var deviceTimestamp: Long = 0L
)
