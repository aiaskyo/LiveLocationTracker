package com.example.livelocationtracker.data.repository

import android.util.Log
import com.example.livelocationtracker.data.model.LocationData
import com.example.livelocationtracker.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Owns all Firestore reads/writes for location data.
 *
 * Reconnect behaviour: Firestore's persistent local cache (enabled in
 * [com.example.livelocationtracker.LiveLocationApp]) applies every write to
 * the local cache synchronously, before the network round-trip even starts.
 * That means [updateLiveLocation] never actually "fails" from the caller's
 * perspective while offline -- the write is queued locally and the SDK
 * automatically flushes the queue in order as soon as the device regains
 * connectivity. We still wrap the network-bound `.await()` calls so a slow
 * or genuinely erroring backend doesn't crash the calling coroutine.
 */
class LocationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Upserts this user's "current position" document at
     * live_locations/{userId}, and appends the same fix to a history
     * sub-collection for later analysis/auditing.
     */
    suspend fun updateLiveLocation(locationData: LocationData) {
        try {
            val userDoc = firestore.collection(Constants.FIRESTORE_COLLECTION_LOCATIONS)
                .document(locationData.userId)

            userDoc.set(locationData, SetOptions.merge()).await()
            userDoc.collection(Constants.FIRESTORE_SUBCOLLECTION_HISTORY)
                .add(locationData)
                .await()
        } catch (e: Exception) {
            // The write is already sitting in Firestore's offline cache and
            // will be retried automatically by the SDK once connectivity is
            // restored, so this is a log line, not a fatal condition.
            Log.w(TAG, "updateLiveLocation: pending sync (will retry automatically)", e)
        }
    }

    /**
     * Streams real-time updates for [userId]'s current position. Works
     * whether the caller is watching their own location or someone else's,
     * as long as Firestore security rules permit the read.
     */
    fun observeLiveLocation(
        userId: String,
        onUpdate: (LocationData?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        firestore.collection(Constants.FIRESTORE_COLLECTION_LOCATIONS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.toObject(LocationData::class.java))
            }

    companion object {
        private const val TAG = "LocationRepository"
    }
}
