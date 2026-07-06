package com.example.livelocationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.livelocationtracker.data.repository.AuthRepository
import com.example.livelocationtracker.service.LocationTrackingService
import com.example.livelocationtracker.utils.Constants
import com.example.livelocationtracker.utils.PermissionHelper

/**
 * Resumes location tracking after a device reboot, but only if all three of
 * these hold:
 *   1. The user had tracking switched on before the reboot (persisted flag),
 *   2. They are still signed in, and
 *   3. All required runtime permissions are still granted.
 *
 * This avoids surprising a user with a tracking notification they never
 * asked to see again, e.g. after they explicitly turned tracking off.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasTrackingEnabled = prefs.getBoolean(Constants.PREF_TRACKING_ENABLED, false)
        val isSignedIn = AuthRepository().isSignedIn()
        val hasPermissions = PermissionHelper.hasAllRequiredPermissions(context)

        if (wasTrackingEnabled && isSignedIn && hasPermissions) {
            LocationTrackingService.start(context)
        }
    }
}
