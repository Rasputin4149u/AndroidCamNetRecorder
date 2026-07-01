package com.example.segmentdriveapp.drive

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.example.segmentdriveapp.util.AppLogger

class DriveAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun authorize(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>, onReady: (String) -> Unit, onError: (String) -> Unit) {
        AppLogger.d(TAG, "Starting Drive authorization request")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                handleAuthorizationResult(result, launcher, onReady)
            }
            .addOnFailureListener { error ->
                AppLogger.e(TAG, "Authorization request failed", error)
                onError("Drive authorization failed: ${error.message}")
            }
    }

    fun consumeAuthorizationResult(result: AuthorizationResult?, onReady: (String) -> Unit, onError: (String) -> Unit) {
        if (result == null) {
            onError("Drive authorization result was empty")
            return
        }
        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) {
            AppLogger.e(TAG, "Authorization result missing access token")
            onError("Drive authorization completed without access token")
            return
        }
        AppLogger.d(TAG, "Received Drive access token")
        prefs.edit { putString(KEY_ACCESS_TOKEN, accessToken) }
        onReady(accessToken)
    }

    fun getCachedAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun clearCachedAccessToken() {
        AppLogger.d(TAG, "Clearing cached access token")
        prefs.edit { remove(KEY_ACCESS_TOKEN) }
    }

    private fun handleAuthorizationResult(
        result: AuthorizationResult,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onReady: (String) -> Unit
    ) {
        if (result.hasResolution()) {
            AppLogger.d(TAG, "Authorization requires user resolution")
            launcher.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
            return
        }
        AppLogger.d(TAG, "Authorization already granted; consuming token immediately")
        consumeAuthorizationResult(result, onReady) { }
    }

    companion object {
        private const val TAG = "DriveAuthManager"
        private const val PREFS = "drive_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
