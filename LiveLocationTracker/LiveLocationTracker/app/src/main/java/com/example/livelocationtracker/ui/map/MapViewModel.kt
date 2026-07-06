package com.example.livelocationtracker.ui.map

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livelocationtracker.data.model.LocationData
import com.example.livelocationtracker.data.repository.AuthRepository
import com.example.livelocationtracker.data.repository.LocationRepository
import com.example.livelocationtracker.utils.Constants
import com.example.livelocationtracker.utils.NetworkConnectivityObserver
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the map screen. Responsibilities:
 *  - Tracks whether tracking is currently switched on (persisted so
 *    BootReceiver can resume it after a reboot).
 *  - Streams the signed-in user's own live location from Firestore so the
 *    map can keep a marker in sync in real time, including fixes written by
 *    the foreground service while the app itself was backgrounded.
 *  - Exposes live connectivity state for the "offline, syncing will resume"
 *    banner.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val locationRepository = LocationRepository()
    private val connectivityObserver = NetworkConnectivityObserver(application)

    private var listenerRegistration: ListenerRegistration? = null

    private val _liveLocation = MutableStateFlow<LocationData?>(null)
    val liveLocation: StateFlow<LocationData?> = _liveLocation

    private val _isTracking = MutableStateFlow(isTrackingPreferenceEnabled())
    val isTracking: StateFlow<Boolean> = _isTracking

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val currentUserId: String?
        get() = authRepository.currentUser?.uid

    init {
        startObservingOwnLocation()
    }

    private fun startObservingOwnLocation() {
        val userId = authRepository.currentUser?.uid ?: return
        listenerRegistration = locationRepository.observeLiveLocation(
            userId = userId,
            onUpdate = { _liveLocation.value = it },
            onError = {
                // Firestore keeps retrying the listener internally; the
                // isOnline banner already communicates connectivity issues
                // to the user, so no extra handling is needed here.
            }
        )
    }

    fun setTrackingEnabled(enabled: Boolean) {
        _isTracking.value = enabled
        prefs().edit().putBoolean(Constants.PREF_TRACKING_ENABLED, enabled).apply()
    }

    private fun isTrackingPreferenceEnabled(): Boolean =
        prefs().getBoolean(Constants.PREF_TRACKING_ENABLED, false)

    private fun prefs() =
        getApplication<Application>().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun signOut() {
        listenerRegistration?.remove()
        authRepository.signOut()
    }

    override fun onCleared() {
        listenerRegistration?.remove()
        super.onCleared()
    }
}
