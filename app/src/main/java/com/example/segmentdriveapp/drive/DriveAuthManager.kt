package com.example.segmentdriveapp.drive

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import com.example.segmentdriveapp.util.AppLogger
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class DriveAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        AppLogger.Initialize(context)
        AppLogger.d(TAG, "init | AppLogger initialized for DriveAuthManager")
    }

    fun authorize(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val RequestedScopeName = DriveScopes.DRIVE_FILE

        AppLogger.d(
            TAG,
            "authorize | input activity=[${activity::class.java.simpleName}] requestedScope=[$RequestedScopeName] cachedTokenBefore=[${prefs.getString(KEY_ACCESS_TOKEN, null)}]"
        )

        val AuthorizationRequestObject = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(RequestedScopeName)))
            .build()

        AppLogger.d(TAG, "authorize | output AuthorizationClient.authorize request built")

        Identity.getAuthorizationClient(activity)
            .authorize(AuthorizationRequestObject)
            .addOnSuccessListener { AuthorizationResultObject ->
                AppLogger.d(
                    TAG,
                    "authorize | output AuthorizationClient.authorize success hasResolution=[${AuthorizationResultObject.hasResolution()}] accessToken=[${AuthorizationResultObject.accessToken}]"
                )
                handleAuthorizationResult(AuthorizationResultObject, launcher, onReady)
            }
            .addOnFailureListener { AuthorizeError ->
                AppLogger.e(TAG, "authorize | output AuthorizationClient.authorize failure", AuthorizeError)
                onError("Drive authorization failed: ${AuthorizeError.message}")
            }
    }

    fun consumeAuthorizationResult(
        result: AuthorizationResult?,
        onReady: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        AppLogger.d(TAG, "consumeAuthorizationResult | input resultIsNull=[${result == null}]")

        if (result == null) {
            AppLogger.e(TAG, "consumeAuthorizationResult | result is null")
            onError("Drive authorization result was empty")
            return
        }

        val AccessToken = result.accessToken
        AppLogger.d(
            TAG,
            "consumeAuthorizationResult | input accessToken=[$AccessToken] hasResolution=[${result.hasResolution()}]"
        )

        if (AccessToken.isNullOrBlank()) {
            AppLogger.e(TAG, "consumeAuthorizationResult | access token missing or blank")
            onError("Drive authorization completed without access token")
            return
        }

        prefs.edit {
            putString(KEY_ACCESS_TOKEN, AccessToken)
        }

        AppLogger.d(TAG, "consumeAuthorizationResult | output SharedPreferences.putString key=[$KEY_ACCESS_TOKEN] value=[$AccessToken]")
        onReady(AccessToken)
    }

    fun getCachedAccessToken(): String? {
        val CachedAccessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        AppLogger.d(TAG, "getCachedAccessToken | output SharedPreferences.getString key=[$KEY_ACCESS_TOKEN] value=[$CachedAccessToken]")
        return CachedAccessToken
    }

    fun clearCachedAccessToken() {
        val CachedAccessTokenBeforeClear = prefs.getString(KEY_ACCESS_TOKEN, null)
        AppLogger.d(TAG, "clearCachedAccessToken | input cachedTokenBeforeClear=[$CachedAccessTokenBeforeClear]")

        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
        }

        AppLogger.d(TAG, "clearCachedAccessToken | output SharedPreferences.remove key=[$KEY_ACCESS_TOKEN]")
    }

    private fun handleAuthorizationResult(
        result: AuthorizationResult,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onReady: (String) -> Unit
    ) {
        AppLogger.d(
            TAG,
            "handleAuthorizationResult | input hasResolution=[${result.hasResolution()}] accessToken=[${result.accessToken}]"
        )

        if (result.hasResolution()) {
            val PendingIntentObject = result.pendingIntent
            AppLogger.d(
                TAG,
                "handleAuthorizationResult | output launcher.launch resolutionPending=[${PendingIntentObject != null}]"
            )
            launcher.launch(IntentSenderRequest.Builder(PendingIntentObject!!.intentSender).build())
            return
        }

        AppLogger.d(TAG, "handleAuthorizationResult | no resolution needed; consuming token immediately")
        consumeAuthorizationResult(result, onReady) { }
    }

    companion object {
        private const val TAG = "DriveAuthManager"
        private const val PREFS = "drive_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
