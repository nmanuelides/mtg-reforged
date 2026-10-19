package io.github.nmanuelides.mtgreforged.platform

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import io.github.nmanuelides.mtgreforged.engine.DriveBackup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Either a ready access token, or a consent screen the UI has to show first. */
private sealed interface Auth {
    data class Token(val value: String) : Auth
    data class NeedsConsent(val intent: PendingIntent) : Auth
}

private suspend fun authorize(activity: Activity): Auth = suspendCancellableCoroutine { cont ->
    val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveBackup.SCOPE))).build()
    Identity.getAuthorizationClient(activity).authorize(request)
        .addOnSuccessListener { result ->
            val pending = result.pendingIntent
            when {
                result.hasResolution() && pending != null -> cont.resume(Auth.NeedsConsent(pending))
                result.accessToken != null -> cont.resume(Auth.Token(result.accessToken!!))
                else -> cont.resumeWithException(IllegalStateException("Google didn't grant Drive access"))
            }
        }
        .addOnFailureListener { cont.resumeWithException(it) }
}

/** The token after the consent screen, or the reason there is none (Google's status code included). */
private fun tokenFrom(activity: Activity, data: Intent?): Result<String> = runCatching {
    val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
    result.accessToken ?: throw IllegalStateException("Google returned no access token")
}.recoverCatching { e ->
    if (e is ApiException) throw IllegalStateException("Google error ${e.statusCode}: ${CommonStatusCodes.getStatusCodeString(e.statusCode)}")
    throw e
}

/**
 * A Drive access token for the signed-in Google account (drive.appdata scope), showing
 * Google's consent screen the first time. Fails with a message the UI can show as is.
 */
@Composable
fun rememberDriveToken(): suspend () -> String {
    val activity = LocalContext.current as? Activity
    val pending = remember { arrayOfNulls<CompletableDeferred<androidx.activity.result.ActivityResult>>(1) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending[0]?.complete(result)
    }
    return remember(activity) {
        suspend {
            val a = activity ?: throw IllegalStateException("Can't reach Google from here.")
            val auth = runCatching { authorize(a) }.getOrElse { throw IllegalStateException("Couldn't connect to Google: ${it.message}") }
            when (auth) {
                is Auth.Token -> auth.value
                is Auth.NeedsConsent -> {
                    val waiting = CompletableDeferred<androidx.activity.result.ActivityResult>().also { pending[0] = it }
                    consent.launch(IntentSenderRequest.Builder(auth.intent.intentSender).build())
                    val result = waiting.await()
                    tokenFrom(a, result.data).getOrElse { first ->
                        // The account / consent was given but the token didn't come back with it: ask once more.
                        val again = runCatching { authorize(a) }.getOrElse {
                            throw IllegalStateException("Google Drive access wasn't granted: ${first.message}; then ${it.message}")
                        }
                        (again as? Auth.Token)?.value
                            ?: throw IllegalStateException("Google Drive access wasn't granted (${first.message}, result ${result.resultCode}).")
                    }
                }
            }
        }
    }
}
