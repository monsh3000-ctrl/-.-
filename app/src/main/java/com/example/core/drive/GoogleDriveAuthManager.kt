package com.example.core.drive

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.core.datastore.StudyLockPreferences
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class GoogleAccountProfile(
    val email: String,
    val displayName: String,
    val isConnected: Boolean,
    val rootFolderId: String = ""
)

class GoogleDriveAuthManager(
    private val context: Context,
    private val preferences: StudyLockPreferences = StudyLockPreferences(context)
) {
    private val credentialManager = CredentialManager.create(context)

    val isConnectedFlow: Flow<Boolean> = preferences.isGoogleDriveConnected
    val accountEmailFlow: Flow<String> = preferences.googleDriveAccount
    val displayNameFlow: Flow<String> = preferences.googleDriveDisplayName
    val rootFolderIdFlow: Flow<String> = preferences.googleDriveRootFolderId

    suspend fun getCurrentProfile(): GoogleAccountProfile = withContext(Dispatchers.IO) {
        val isConnected = preferences.isGoogleDriveConnected.first()
        val email = preferences.googleDriveAccount.first()
        val name = preferences.googleDriveDisplayName.first().ifBlank {
            if (email.contains("@")) email.substringBefore("@").replace(".", " ").capitalize() else "Google User"
        }
        val folderId = preferences.googleDriveRootFolderId.first()
        GoogleAccountProfile(
            email = email,
            displayName = name,
            isConnected = isConnected && email.isNotBlank(),
            rootFolderId = folderId
        )
    }

    /**
     * Authenticates with Google using Jetpack CredentialManager,
     * allowing the user to pick any Google Account on the device.
     */
    suspend fun signInWithGoogleCredential(activityContext: Context): Result<GoogleAccountProfile> = withContext(Dispatchers.IO) {
        try {
            // Build GoogleIdOption requesting Google account and Drive scopes
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId("739121955561.apps.googleusercontent.com")
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")

                preferences.setGoogleDriveConnected(
                    connected = true,
                    accountEmail = email,
                    displayName = displayName,
                    rootFolderId = "studylock_root_${email.hashCode()}"
                )

                Result.success(
                    GoogleAccountProfile(
                        email = email,
                        displayName = displayName,
                        isConnected = true,
                        rootFolderId = "studylock_root_${email.hashCode()}"
                    )
                )
            } else {
                Result.failure(IllegalArgumentException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: Exception) {
            // If device Play Services doesn't have Web Client ID or user cancelled,
            // provide fallback to custom email linking
            Result.failure(e)
        }
    }

    suspend fun signInWithCredentialManager(activityContext: Context): Result<GoogleAccountProfile> =
        signInWithGoogleCredential(activityContext)

    /**
     * Links any user-specified Google Account (e.g. user enters their student email
     * or school Google Workspace account directly).
     */
    suspend fun linkCustomGoogleAccount(
        email: String,
        displayName: String = ""
    ): GoogleAccountProfile = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val name = if (displayName.isNotBlank()) displayName.trim() else {
            cleanEmail.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        val folderId = "drive_root_studylock_${Math.abs(cleanEmail.hashCode())}"

        preferences.setGoogleDriveConnected(
            connected = true,
            accountEmail = cleanEmail,
            displayName = name,
            rootFolderId = folderId
        )

        GoogleAccountProfile(
            email = cleanEmail,
            displayName = name,
            isConnected = true,
            rootFolderId = folderId
        )
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        preferences.disconnectGoogleDrive()
    }
}
