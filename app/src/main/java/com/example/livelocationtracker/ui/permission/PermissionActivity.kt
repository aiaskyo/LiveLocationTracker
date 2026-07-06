package com.example.livelocationtracker.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.livelocationtracker.R
import com.example.livelocationtracker.databinding.ActivityPermissionBinding
import com.example.livelocationtracker.ui.map.MapActivity
import com.example.livelocationtracker.utils.BatteryOptimizationHelper
import com.example.livelocationtracker.utils.PermissionHelper

/**
 * Walks the user through every permission this app needs, one logical step
 * at a time, following the pattern Android 10+ requires:
 *
 *   1. Foreground location (FINE + COARSE) - requested together.
 *   2. POST_NOTIFICATIONS (Android 13+) - needed to show the ongoing
 *      tracking notification required by the foreground service.
 *   3. Background location ("Allow all the time") - MUST be requested in a
 *      separate, isolated request, only after foreground access is already
 *      granted. On Android 11+ the system dialog no longer reliably offers
 *      an "Allow all the time" radio button from an in-app request, so we
 *      route the user to the app's location settings page instead, which is
 *      Google's own recommended pattern for API 30+.
 *   4. Battery optimization exemption - not a runtime "permission" in the
 *      Android sense, but just as critical for uninterrupted tracking, so
 *      it's folded into the same guided flow.
 *
 * The screen re-evaluates its state every time it resumes, so coming back
 * from Settings (e.g. after manually granting "Allow all the time") is
 * picked up automatically.
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding

    private val requestForegroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshUi() }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshUi() }

    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshUi() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonForegroundLocation.setOnClickListener {
            requestForegroundLocation.launch(PermissionHelper.foregroundLocationPermissions)
        }

        binding.buttonNotifications.setOnClickListener {
            PermissionHelper.notificationPermission.firstOrNull()?.let {
                requestNotificationPermission.launch(it)
            }
        }

        binding.buttonBackgroundLocation.setOnClickListener {
            showBackgroundLocationRationale()
        }

        binding.buttonBatteryOptimization.setOnClickListener {
            settingsLauncher.launch(BatteryOptimizationHelper.buildRequestIgnoreOptimizationsIntent(this))
        }

        binding.buttonContinue.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    /** Explains why background access is needed before sending the user onward. */
    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_background_dialog_title)
            .setMessage(R.string.permission_background_dialog_message)
            .setPositiveButton(R.string.permission_background_dialog_positive) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Google's recommended pattern for API 30+: send the user
                    // to the app's own location permission settings page,
                    // since the in-app runtime dialog may not present the
                    // "Allow all the time" option on these OS versions.
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    settingsLauncher.launch(intent)
                } else {
                    PermissionHelper.backgroundLocationPermission.firstOrNull()?.let {
                        requestBackgroundLocation.launch(it)
                    }
                }
            }
            .setNegativeButton(R.string.permission_dialog_cancel, null)
            .show()
    }

    private fun refreshUi() {
        val hasForeground = PermissionHelper.hasForegroundLocationPermission(this)
        val hasBackground = PermissionHelper.hasBackgroundLocationPermission(this)
        val hasNotifications = PermissionHelper.hasNotificationPermission(this)
        val batteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)

        setStepIcon(binding.iconForegroundLocation, hasForeground)
        setStepIcon(binding.iconNotifications, hasNotifications)
        setStepIcon(binding.iconBackgroundLocation, hasBackground)
        setStepIcon(binding.iconBatteryOptimization, batteryExempt)

        binding.buttonForegroundLocation.isEnabled = !hasForeground
        binding.buttonNotifications.isEnabled = !hasNotifications
        binding.buttonBackgroundLocation.isEnabled = hasForeground && !hasBackground
        binding.buttonBatteryOptimization.isEnabled = !batteryExempt

        // Notifications and the battery exemption improve reliability but
        // are not strictly load-bearing for correctness, so "Continue" is
        // gated only on the two location permissions tracking cannot work
        // without.
        binding.buttonContinue.isEnabled = hasForeground && hasBackground
    }

    private fun setStepIcon(icon: android.widget.ImageView, done: Boolean) {
        icon.setImageResource(
            if (done) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked
        )
        icon.setColorFilter(
            ContextCompat.getColor(
                this,
                if (done) R.color.md_theme_primary else R.color.md_theme_outline
            )
        )
    }
}
