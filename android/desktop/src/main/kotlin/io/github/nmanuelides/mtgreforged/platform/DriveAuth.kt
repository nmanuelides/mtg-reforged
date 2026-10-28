package io.github.nmanuelides.mtgreforged.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.engine.DriveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A Drive access token for the user's Google account (drive.appdata scope). The first time,
 * Google's sign-in opens in the browser (OAuth for installed apps: loopback redirect + PKCE);
 * after that a saved refresh token gets new access tokens quietly.
 * Fails with a message the UI can show as is.
 */
@Composable
fun rememberDriveToken(): suspend () -> String = remember { suspend { DriveAuth.token() } }

/** A Drive token only if the player signed in before (never opens the browser), else null. */
@Composable
fun rememberQuietDriveToken(): suspend () -> String? = remember { suspend { runCatching { DriveAuth.quietToken() }.getOrNull() } }

private object DriveAuth {
    private const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN = "https://oauth2.googleapis.com/token"
    private const val KEY_REFRESH = "refreshToken"

    private var accessToken: String? = null
    private var expiresAt = 0L

    private val prefs get() = DesktopContext.default().prefs("drive")

    suspend fun token(): String = runInterruptible(Dispatchers.IO) {
        check(BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) { "Google Drive isn't set up in this build of the app; use Save file instead." }
        synchronized(this) {
            accessToken?.takeIf { System.currentTimeMillis() < expiresAt }?.let { return@runInterruptible it }
            prefs.getString(KEY_REFRESH, null)?.let { refresh ->
                runCatching { exchange("grant_type" to "refresh_token", "refresh_token" to refresh) }.getOrNull()?.let { return@runInterruptible it }
            }
            signIn()
        }
    }

    suspend fun quietToken(): String? = runInterruptible(Dispatchers.IO) {
        if (BuildConfig.GOOGLE_CLIENT_ID.isBlank()) return@runInterruptible null
        synchronized(this) {
            accessToken?.takeIf { System.currentTimeMillis() < expiresAt }
                ?: prefs.getString(KEY_REFRESH, null)?.let { refresh ->
                    runCatching { exchange("grant_type" to "refresh_token", "refresh_token" to refresh) }.getOrNull()
                }
        }
    }

    /** Opens Google's consent page in the browser and waits for it to come back to us. */
    private fun signIn(): String {
        val random = SecureRandom()
        fun token(bytes: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))
        val verifier = token(48)
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = token(16)

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 5 * 60_000
            val redirect = "http://127.0.0.1:${server.localPort}"
            val url = AUTH + "?" + form(
                "client_id" to BuildConfig.GOOGLE_CLIENT_ID,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to DriveBackup.SCOPE,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent",
            )
            runCatching { Desktop.getDesktop().browse(URI(url)) }
                .onFailure { throw IllegalStateException("Couldn't open the browser to sign in to Google: ${it.message}") }

            val params = runCatching {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader().readLine().orEmpty()
                    val query = request.substringAfter("?", "").substringBefore(" ")
                    val params = query.split("&").filter { "=" in it }.associate {
                        URLDecoder.decode(it.substringBefore("="), "UTF-8") to URLDecoder.decode(it.substringAfter("="), "UTF-8")
                    }
                    val ok = params["code"] != null
                    val page = "<html><body style=\"font-family:Georgia,serif;text-align:center;margin-top:15%\"><h2>" +
                        (if (ok) "Signed in. You can close this tab and go back to MTG Reforged." else "Google didn't grant access.") +
                        "</h2></body></html>"
                    val body = page.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                    params
                }
            }.getOrElse { throw IllegalStateException("Google sign-in didn't finish: ${it.message}") }

            check(params["state"] == state) { "Google sign-in came back from somewhere else; try again." }
            val code = params["code"] ?: throw IllegalStateException("Google Drive access wasn't granted (${params["error"] ?: "cancelled"}).")
            return exchange(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirect,
                "code_verifier" to verifier,
            )
        }
    }

    /** Calls Google's token endpoint; keeps the access token and any new refresh token. */
    private fun exchange(vararg fields: Pair<String, String>): String {
        val body = form(*fields, "client_id" to BuildConfig.GOOGLE_CLIENT_ID, "client_secret" to BuildConfig.GOOGLE_CLIENT_SECRET).toByteArray()
        val c = URL(TOKEN).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            c.outputStream.use { it.write(body) }
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                if (fields.any { it.second == "refresh_token" }) prefs.edit().remove(KEY_REFRESH).apply()
                throw IllegalStateException("Google answered ${c.responseCode} ${err.take(200)}")
            }
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { prefs.edit().putString(KEY_REFRESH, it).apply() }
            val token = json.getString("access_token")
            accessToken = token
            expiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
            return token
        } finally {
            c.disconnect()
        }
    }

    private fun form(vararg fields: Pair<String, String>) =
        fields.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }
}
