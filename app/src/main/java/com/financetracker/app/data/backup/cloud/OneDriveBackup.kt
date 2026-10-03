package com.financetracker.app.data.backup.cloud

import android.net.Uri
import android.util.Base64
import com.financetracker.app.BuildConfig
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uploads the automatic backup to the user's OneDrive through Microsoft Graph, signed in with the
 * standard browser sign-in (OAuth 2.0 authorization code + PKCE — no password ever passes through
 * the app). Backups go to the app's own folder (OneDrive > Apps > the app's name); sign-in also
 * asks for normal file access once, only so OneDrive sets that folder up (see [SIGN_IN_SCOPES]),
 * and every upload uses a token limited to the app's folder.
 *
 * Needs the app's Microsoft "Application (client) ID" (ONEDRIVE_CLIENT_ID in the build
 * environment), registered as a mobile app with the redirect URI [REDIRECT_URI].
 */
object OneDriveBackup {
    const val REDIRECT_URI = "financetracker://onedrive-auth"
    private const val AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
    /** What weekly uploads use: only the app's own folder. */
    private const val UPLOAD_SCOPES = "openid profile offline_access Files.ReadWrite.AppFolder"

    /** Sign-in also asks for normal file access once: OneDrive only sets up a new app's own
     * folder when that's granted (with the app-folder scope alone a new app's folder stays
     * "not found" — a known OneDrive problem). After that, uploads go back to [UPLOAD_SCOPES]. */
    private const val SIGN_IN_SCOPES = "$UPLOAD_SCOPES Files.ReadWrite"
    private const val GRAPH = "https://graph.microsoft.com/v1.0"

    val isConfigured: Boolean get() = BuildConfig.ONEDRIVE_CLIENT_ID.isNotBlank()

    data class PendingSignIn(val url: String, val state: String, val codeVerifier: String)

    data class Tokens(val accessToken: String, val refreshToken: String, val account: String?)

    fun startSignIn(): PendingSignIn {
        val random = SecureRandom()
        val verifier = randomUrlSafe(random, 48)
        val state = randomUrlSafe(random, 16)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = Uri.parse("$AUTHORITY/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SIGN_IN_SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
            .toString()
        return PendingSignIn(url, state, verifier)
    }

    /** Exchanges the sign-in redirect's code for tokens. Blocking — call off the main thread. */
    fun completeSignIn(code: String, codeVerifier: String): Tokens = token(
        SIGN_IN_SCOPES,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to REDIRECT_URI,
        "code_verifier" to codeVerifier
    )

    /** A fresh access token (and possibly a new refresh token) for a background upload. */
    fun refresh(refreshToken: String): Tokens = token(
        UPLOAD_SCOPES,
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken
    )

    /** Makes OneDrive create the app's own folder (OneDrive > Apps > the app's name), using the
     * sign-in token that has normal file access — see [SIGN_IN_SCOPES]. */
    fun provisionAppFolder(accessToken: String) {
        val response = CloudHttp.request(
            "GET",
            "$GRAPH/me/drive/special/approot",
            headers = mapOf("Authorization" to "Bearer $accessToken")
        )
        if (!response.ok) {
            throw CloudException("OneDrive couldn't set up the app's folder (${response.status}): ${errorText(response.body)}", response.status)
        }
    }

    /** Writes [bytes] to [fileName] in the app's OneDrive folder, replacing the previous copy. */
    fun upload(accessToken: String, fileName: String, bytes: ByteArray) {
        val response = CloudHttp.request(
            "PUT",
            "$GRAPH/me/drive/special/approot:/${Uri.encode(fileName)}:/content",
            headers = mapOf("Authorization" to "Bearer $accessToken"),
            body = bytes,
            contentType = "application/octet-stream"
        )
        if (!response.ok) throw CloudException("OneDrive upload failed (${response.status}): ${errorText(response.body)}", response.status)
    }

    private fun token(scopes: String, vararg grant: Pair<String, String>): Tokens {
        val response = CloudHttp.request(
            "POST",
            "$AUTHORITY/token",
            body = CloudHttp.form("client_id" to BuildConfig.ONEDRIVE_CLIENT_ID, "scope" to scopes, *grant),
            contentType = "application/x-www-form-urlencoded"
        )
        if (!response.ok) {
            throw CloudException("OneDrive sign-in failed (${response.status}): ${errorText(response.body)}", response.status)
        }
        val json = JSONObject(response.body)
        return Tokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifEmpty { grant.firstOrNull { it.first == "refresh_token" }?.second.orEmpty() },
            account = json.optString("id_token").takeIf { it.isNotEmpty() }?.let(::accountFromIdToken)
        )
    }

    private fun accountFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.')[1]
        val claims = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        claims.optString("preferred_username").ifEmpty { claims.optString("email") }.ifEmpty { null }
    }.getOrNull()

    private fun errorText(body: String): String = runCatching {
        val json = JSONObject(body)
        json.optString("error_description").ifEmpty {
            json.optJSONObject("error")?.optString("message") ?: json.optString("error")
        }
    }.getOrNull()?.lineSequence()?.firstOrNull()?.take(200) ?: body.take(200)

    private fun randomUrlSafe(random: SecureRandom, bytes: Int): String =
        base64Url(ByteArray(bytes).also { random.nextBytes(it) })

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
