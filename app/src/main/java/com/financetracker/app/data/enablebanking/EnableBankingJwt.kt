package com.financetracker.app.data.enablebanking

import android.util.Base64
import java.security.Signature

/**
 * Signs short-lived RS256 JWTs for Enable Banking's API. Their auth model has no client
 * secret: every request is authorized by a JWT whose header names this app's registered
 * `kid` (its Application ID) and whose signature is produced with the matching private key
 * generated in the Enable Banking Control Panel — the user's own registration or the built-in one
 * (see [EnableBankingCredentials]). Uses only platform java.security APIs so
 * this never depends on a third-party crypto/JWT library.
 */
internal object EnableBankingJwt {

    val isConfigured: Boolean get() = EnableBankingCredentials.state.value.isConfigured

    /** Returns a freshly-signed JWT valid for one hour, or null if not configured. */
    fun create(): String? {
        val credentials = EnableBankingCredentials.current() ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"typ":"JWT","alg":"RS256","kid":"${credentials.applicationId}"}"""
        val payload = """{"iss":"enablebanking.com","aud":"api.enablebanking.com","iat":$nowSeconds,"exp":${nowSeconds + 3600}}"""
        val signingInput = "${b64url(header.toByteArray(Charsets.UTF_8))}.${b64url(payload.toByteArray(Charsets.UTF_8))}"

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(credentials.privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        return "$signingInput.${b64url(signature)}"
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
