package com.example.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.core.datastore.StudyLockPreferences
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class UserSession(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val isAuthenticated: Boolean = true,
    val lastLoginAt: Long = System.currentTimeMillis()
)

class GoogleAuthManager(
    private val context: Context,
    private val preferences: StudyLockPreferences = StudyLockPreferences(context)
) {
    companion object {
        private const val TAG = "GoogleAuthManager"
        // Google Web Client ID for OAuth / ID Token Verification
        const val DEFAULT_WEB_CLIENT_ID = "684096953785.apps.googleusercontent.com"
    }

    private val credentialManager = CredentialManager.create(context)

    val currentSessionFlow: Flow<UserSession?> = combine(
        preferences.isLoggedIn,
        preferences.userEmail,
        preferences.userDisplayName,
        preferences.userPhotoUrl
    ) { isLoggedIn, email, name, photoUrl ->
        if (isLoggedIn && email.isNotBlank()) {
            UserSession(
                email = email,
                displayName = name.ifBlank { email.substringBefore("@") },
                photoUrl = photoUrl,
                isAuthenticated = true
            )
        } else {
            null
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(DEFAULT_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val email = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
            val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
            val idToken = googleIdTokenCredential.idToken

            preferences.saveUserSession(
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                idToken = idToken
            )

            val session = UserSession(
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                idToken = idToken,
                isAuthenticated = true,
                lastLoginAt = System.currentTimeMillis()
            )

            Log.i(TAG, "Google Sign-In successful for $email")
            Result.success(session)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User canceled Google sign-in")
            Result.failure(Exception("Sign-in was canceled"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.message}", e)
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            preferences.clearUserSession()
            Log.i(TAG, "User signed out")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
            Result.failure(e)
        }
    }
}
