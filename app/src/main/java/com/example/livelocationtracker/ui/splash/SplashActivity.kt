package com.example.livelocationtracker.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.livelocationtracker.data.repository.AuthRepository
import com.example.livelocationtracker.ui.auth.LoginActivity
import com.example.livelocationtracker.ui.map.MapActivity
import com.example.livelocationtracker.ui.permission.PermissionActivity
import com.example.livelocationtracker.utils.PermissionHelper

/**
 * Routes the user to the correct starting screen:
 *   not signed in            -> LoginActivity
 *   signed in, missing perms -> PermissionActivity
 *   signed in, fully granted -> MapActivity
 *
 * Kept intentionally free of any UI beyond the launch theme's windowBackground
 * (see themes.xml) so it disappears instantly once routing decides where to go.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val destination = when {
            !AuthRepository().isSignedIn() -> LoginActivity::class.java
            !PermissionHelper.hasAllRequiredPermissions(this) -> PermissionActivity::class.java
            else -> MapActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }
}
