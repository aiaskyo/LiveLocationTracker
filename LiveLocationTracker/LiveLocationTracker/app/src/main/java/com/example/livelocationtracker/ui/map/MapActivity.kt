package com.example.livelocationtracker.ui.map

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.livelocationtracker.R
import com.example.livelocationtracker.data.model.LocationData
import com.example.livelocationtracker.databinding.ActivityMapBinding
import com.example.livelocationtracker.service.LocationTrackingService
import com.example.livelocationtracker.ui.splash.SplashActivity
import com.example.livelocationtracker.utils.PermissionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch

/**
 * Main screen: shows the signed-in user's live location on a Google Map and
 * lets them start/stop the background tracking service. If required
 * permissions are missing (e.g. revoked from system settings after the
 * initial grant), the user is routed back to the permission flow.
 */
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding
    private val viewModel: MapViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var currentMarker: com.google.android.gms.maps.model.Marker? = null
    private var hasCenteredCamera = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragmentContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabToggleTracking.setOnClickListener { onToggleTrackingClicked() }
        binding.buttonSignOut.setOnClickListener { onSignOutClicked() }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionHelper.hasAllRequiredPermissions(this)) {
            // A permission may have been revoked from system settings while
            // the app was backgrounded; send the user back through the flow.
            startActivity(Intent(this, com.example.livelocationtracker.ui.permission.PermissionActivity::class.java))
        }
        refreshTrackingButton(viewModel.isTracking.value)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        viewModel.liveLocation.value?.let { renderLocation(it) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.liveLocation.collect { location ->
                location?.let { renderLocation(it) }
            }
        }
        lifecycleScope.launch {
            viewModel.isTracking.collect { refreshTrackingButton(it) }
        }
        lifecycleScope.launch {
            viewModel.isOnline.collect { online ->
                binding.offlineBanner.visibility =
                    if (online) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
    }

    private fun renderLocation(location: LocationData) {
        val map = googleMap ?: return
        val latLng = LatLng(location.latitude, location.longitude)

        if (currentMarker == null) {
            currentMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.map_marker_title))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            currentMarker?.position = latLng
        }

        val zoom = if (hasCenteredCamera) map.cameraPosition.zoom else 16f
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        hasCenteredCamera = true

        binding.textLocationDetails.text = getString(
            R.string.map_location_details_format,
            location.latitude,
            location.longitude,
            location.accuracy,
            location.speed,
            location.bearing
        )
    }

    private fun onToggleTrackingClicked() {
        if (!PermissionHelper.hasAllRequiredPermissions(this)) {
            startActivity(Intent(this, com.example.livelocationtracker.ui.permission.PermissionActivity::class.java))
            return
        }

        val startingNow = !viewModel.isTracking.value
        if (startingNow) {
            LocationTrackingService.start(this)
        } else {
            LocationTrackingService.stop(this)
        }
        viewModel.setTrackingEnabled(startingNow)
    }

    private fun refreshTrackingButton(isTracking: Boolean) {
        binding.fabToggleTracking.text = getString(
            if (isTracking) R.string.action_stop_tracking else R.string.action_start_tracking
        )
        binding.fabToggleTracking.setIconResource(
            if (isTracking) R.drawable.ic_stop else R.drawable.ic_play
        )
    }

    private fun onSignOutClicked() {
        LocationTrackingService.stop(this)
        viewModel.setTrackingEnabled(false)
        viewModel.signOut()
        startActivity(
            Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
