package com.financetracker.app.data.enablebanking

import android.util.Base64
import com.financetracker.app.BuildConfig
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Signs short-lived RS256 JWTs for Enable Banking's API. Their auth model has no client
 * secret: every request is authorized by a JWT whose header names this app's registered
 * `kid` (its Application ID) and whose signature is produced with the matching private key
 * generated in the Enable Banking Control Panel. Uses only platform java.security APIs so
 * this never depends on a third-party crypto/JWT library.
 */
internal object EnableBankingJwt {

    val isConfigured: Boolean
        get() = BuildConfig.ENABLE_BANKING_APPLICATION_ID.isNotBlank() &&
            BuildConfig.ENABLE_BANKING_PRIVATE_KEY_B64.isNotBlank()

    private val privateKey: PrivateKey? by lazy {
        if (!isConfigured) return@lazy null
        val pem = String(Base64.decode(BuildConfig.ENABLE_BANKING_PRIVATE_KEY_B64, Base64.DEFAULT), Charsets.UTF_8)
        val der = Base64.decode(
            pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), ""),
            Base64.DEFAULT
        )
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    /** Returns a freshly-signed JWT valid for one hour, or null if not configured. */
    fun create(): String? {
        val key = privateKey ?: return null
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"typ":"JWT","alg":"RS256","kid":"${BuildConfig.ENABLE_BANKING_APPLICATION_ID}"}"""
        val payload = """{"iss":"enablebanking.com","aud":"api.enablebanking.com","iat":$nowSeconds,"exp":${nowSeconds + 3600}}"""
        val signingInput = "${b64url(header.toByteArray(Charsets.UTF_8))}.${b64url(payload.toByteArray(Charsets.UTF_8))}"

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(key)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        return "$signingInput.${b64url(signature)}"
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
