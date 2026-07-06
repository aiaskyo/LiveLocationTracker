package com.example.livelocationtracker.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Continuous background GPS tracking is exactly the kind of workload
 * aggressive OEM battery managers (and stock Doze mode) love to kill. This
 * helper lets the UI check whether the app is currently exempt from battery
 * optimizations and, if not, deep-link the user straight to the system
 * dialog / settings screen that lets them grant the exemption.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system's "allow this app to ignore battery optimizations?"
     * confirmation dialog directly. Requires the
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission (already declared in
     * the manifest). Play Store policy restricts this to apps whose core
     * function requires it -- a continuous live-tracking app qualifies, but
     * always let the user say no and fall back gracefully.
     */
    @SuppressLint("BatteryLife")
    fun buildRequestIgnoreOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Fallback for OEMs (MIUI, EMUI, ColorOS, etc.) that ignore or heavily
     * restrict the direct request intent above -- sends the user to the
     * general system list where they can find this app and whitelist it
     * manually.
     */
    fun buildBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
