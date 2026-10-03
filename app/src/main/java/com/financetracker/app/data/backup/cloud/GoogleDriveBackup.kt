package com.financetracker.app.data.backup.cloud

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.util.UUID

/**
 * Uploads the automatic backup to the user's Google Drive through the Drive REST API, authorized
 * with Google's own on-phone account picker (Google Play services). The app only gets access to
 * files it creates itself (the drive.file scope), never the rest of the user's Drive.
 *
 * Google identifies the app by its package name and signing certificate, registered as an
 * Android OAuth client in a Google Cloud project with the Drive API enabled — no id in the code.
 */
object GoogleDriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun accountOf(result: AuthorizationResult): String? =
        runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()

    /** An access token for a background upload, without any UI. Blocking — call off the main
     * thread. Throws when the user has to approve access again. */
    fun accessToken(context: Context): String {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request))
        if (result.hasResolution()) throw CloudException("Google Drive needs you to sign in again.")
        return result.accessToken ?: throw CloudException("Google Drive didn't return access.")
    }

    /** Writes [bytes] to the backup file — updating [fileId] when it still exists, otherwise
     * creating [fileName] — and returns the file's id for next time. */
    fun upload(accessToken: String, fileId: String?, fileName: String, bytes: ByteArray): String {
        val auth = mapOf("Authorization" to "Bearer $accessToken")
        if (fileId != null) {
            val update = CloudHttp.request(
                "POST",
                "$UPLOAD/${CloudHttp.enc(fileId)}?uploadType=media&fields=id",
                headers = auth + ("X-HTTP-Method-Override" to "PATCH"),
                body = bytes,
                contentType = "application/octet-stream"
            )
            if (update.ok) return fileId
            if (update.status != 404) throw failure(update)
        }
        val boundary = "ft-" + UUID.randomUUID()
        val metadata = JSONObject().put("name", fileName).put("mimeType", "application/octet-stream").toString()
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8) +
            bytes + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val create = CloudHttp.request(
            "POST",
            "$UPLOAD?uploadType=multipart&fields=id",
            headers = auth,
            body = body,
            contentType = "multipart/related; boundary=$boundary"
        )
        if (!create.ok) throw failure(create)
        return JSONObject(create.body).getString("id")
    }

    private fun failure(response: CloudHttp.Response): CloudException {
        val message = runCatching { JSONObject(response.body).getJSONObject("error").getString("message") }
            .getOrNull() ?: response.body.take(200)
        return CloudException("Google Drive upload failed (${response.status}): $message", response.status)
    }
}
