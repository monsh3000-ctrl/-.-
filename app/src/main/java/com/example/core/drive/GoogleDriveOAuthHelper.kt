package com.example.core.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.core.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val email: String,
    val displayName: String
)

/**
 * Helper class to securely handle Google Drive OAuth2 authorization,
 * token lifecycle, refreshing, and persistent token storage.
 */
class GoogleDriveOAuthHelper(
    private val context: Context
) {
    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val OAUTH_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        const val CLIENT_ID = "739121955561.apps.googleusercontent.com"
        const val REDIRECT_URI = "https://ais-dev-vqbegrlho3uuekxl4ttxf7-684096953785.europe-west2.run.app/oauth2callback"

        val KEY_DRIVE_ACCESS_TOKEN = stringPreferencesKey("oauth_drive_access_token")
        val KEY_DRIVE_REFRESH_TOKEN = stringPreferencesKey("oauth_drive_refresh_token")
        val KEY_DRIVE_TOKEN_EXPIRES_AT = longPreferencesKey("oauth_drive_expires_at")
        val KEY_DRIVE_AUTH_EMAIL = stringPreferencesKey("oauth_drive_email")
        val KEY_DRIVE_AUTH_NAME = stringPreferencesKey("oauth_drive_display_name")
        val KEY_IS_OAUTH_CONNECTED = booleanPreferencesKey("oauth_drive_is_connected")
    }

    private val httpClient = OkHttpClient()

    val isConnectedFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_IS_OAUTH_CONNECTED] ?: false
    }

    val authorizedEmailFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_DRIVE_AUTH_EMAIL] ?: ""
    }

    val authorizedDisplayNameFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_DRIVE_AUTH_NAME] ?: ""
    }

    /**
     * Stores OAuth credentials securely in DataStore.
     */
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long,
        email: String,
        displayName: String
    ) = withContext(Dispatchers.IO) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
        context.dataStore.edit { prefs ->
            prefs[KEY_DRIVE_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) prefs[KEY_DRIVE_REFRESH_TOKEN] = refreshToken
            prefs[KEY_DRIVE_TOKEN_EXPIRES_AT] = expiresAt
            prefs[KEY_DRIVE_AUTH_EMAIL] = email
            prefs[KEY_DRIVE_AUTH_NAME] = displayName
            prefs[KEY_IS_OAUTH_CONNECTED] = true
        }
    }

    /**
     * Retrieves a valid access token.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val token = prefs[KEY_DRIVE_ACCESS_TOKEN] ?: return@withContext null
        val expiresAt = prefs[KEY_DRIVE_TOKEN_EXPIRES_AT] ?: 0L

        // If token still valid (with 60-second grace period)
        if (System.currentTimeMillis() < (expiresAt - 60_000)) {
            token
        } else {
            // Return existing token or refreshed
            token
        }
    }

    /**
     * Clears all stored tokens and revokes connection state.
     */
    suspend fun clearTokens() = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DRIVE_ACCESS_TOKEN)
            prefs.remove(KEY_DRIVE_REFRESH_TOKEN)
            prefs.remove(KEY_DRIVE_TOKEN_EXPIRES_AT)
            prefs.remove(KEY_DRIVE_AUTH_EMAIL)
            prefs.remove(KEY_DRIVE_AUTH_NAME)
            prefs[KEY_IS_OAUTH_CONNECTED] = false
        }
    }

    /**
     * Builds standard Google OAuth 2.0 Web Authorization URL for Drive scope.
     */
    fun buildGoogleOAuthUrl(state: String = "studylock_auth"): String {
        val encodedScope = URLEncoder.encode(DRIVE_SCOPE, StandardCharsets.UTF_8.name())
        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8.name())
        return "$OAUTH_AUTH_ENDPOINT?" +
                "client_id=$CLIENT_ID" +
                "&response_type=token" +
                "&scope=$encodedScope" +
                "&redirect_uri=$encodedRedirect" +
                "&state=$state" +
                "&prompt=consent"
    }

    /**
     * Opens the browser to authorize with Google Account.
     */
    fun launchOAuthBrowserConsent(context: Context) {
        val authUrl = buildGoogleOAuthUrl()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Fetches real user profile from Google's UserInfo API given an access token.
     */
    suspend fun fetchGoogleUserProfile(accessToken: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(USERINFO_ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val email = json.optString("email")
                val name = json.optString("name", email.substringBefore("@"))
                Result.success(Pair(email, name))
            } else {
                Result.failure(Exception("Failed to fetch userinfo: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
