package com.example.livelocationtracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around FirebaseAuth that exposes suspend functions instead of
 * the raw Task API, and normalizes results into [Result] so the ViewModel
 * layer doesn't need to deal with exceptions directly.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun isSignedIn(): Boolean = auth.currentUser != null

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user ?: error("Sign-in succeeded but returned no user")
        }

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user ?: error("Registration succeeded but returned no user")
        }

    /** Convenient for quick demos/testing without building a full sign-up flow. */
    suspend fun signInAnonymously(): Result<FirebaseUser> =
        runCatching {
            val result = auth.signInAnonymously().await()
            result.user ?: error("Anonymous sign-in succeeded but returned no user")
        }

    fun signOut() = auth.signOut()
}
