package com.financetracker.app.data.enablebanking

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.financetracker.app.BuildConfig
import com.financetracker.app.data.backup.DeviceSecret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/** Where the app sends the browser back to after a bank approval. Every Enable Banking
 * registration used with this app must list it as a redirect URL. */
const val ENABLE_BANKING_REDIRECT_URL = "https://ttinorua.github.io/enablebanking-redirect/"

data class EnableBankingCredentialsState(
    /** The user's own registration's Application ID, or null when using the one built into the app. */
    val ownApplicationId: String? = null
) {
    val hasBuiltIn: Boolean
        get() = BuildConfig.ENABLE_BANKING_APPLICATION_ID.isNotBlank() && BuildConfig.ENABLE_BANKING_PRIVATE_KEY_B64.isNotBlank()
    val isConfigured: Boolean get() = ownApplicationId != null || hasBuiltIn
}

/**
 * Which Enable Banking registration (Application ID + private key) the app signs its bank requests
 * with: the user's own, entered in Settings > Bank, or else the one built into the app. With their
 * own registration each person connects their own bank accounts, independent of anyone else's.
 *
 * The private key is stored encrypted with this phone's Android Keystore key (and left out of
 * backups).
 */
object EnableBankingCredentials {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_OWN_APP_ID = "enablebanking_own_app_id"
    private const val KEY_OWN_PRIVATE_KEY = "enablebanking_own_private_key"

    /** Phone-specific secrets, never written into a backup. */
    val SECRET_KEYS = setOf(KEY_OWN_APP_ID, KEY_OWN_PRIVATE_KEY)

    private val APPLICATION_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    data class Credentials(val applicationId: String, val privateKey: PrivateKey)

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(EnableBankingCredentialsState())
    val state: StateFlow<EnableBankingCredentialsState> = _state.asStateFlow()

    @Volatile
    private var cached: Credentials? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refresh()
    }

    private fun refresh() {
        cached = null
        _state.value = EnableBankingCredentialsState(
            ownApplicationId = prefs.getString(KEY_OWN_APP_ID, null)?.takeIf { prefs.contains(KEY_OWN_PRIVATE_KEY) }
        )
    }

    /** Checks and saves the user's own registration. Fails with a message to show if the ID or
     * key file isn't valid. */
    fun saveOwn(applicationId: String, pem: String): Result<Unit> = runCatching {
        val id = applicationId.trim()
        require(APPLICATION_ID.matches(id)) { "That doesn't look like an Application ID (e.g. 1a2b3c4d-…)." }
        parsePrivateKey(pem)
        prefs.edit()
            .putString(KEY_OWN_APP_ID, id)
            .putString(KEY_OWN_PRIVATE_KEY, DeviceSecret.encrypt(pem.toByteArray(Charsets.UTF_8)))
            .commit()
        refresh()
    }

    fun removeOwn() {
        prefs.edit().remove(KEY_OWN_APP_ID).remove(KEY_OWN_PRIVATE_KEY).commit()
        refresh()
    }

    /** The registration to sign requests with, or null if there's none. */
    fun current(): Credentials? {
        cached?.let { return it }
        val own = if (::prefs.isInitialized) {
            val id = prefs.getString(KEY_OWN_APP_ID, null)
            val pem = prefs.getString(KEY_OWN_PRIVATE_KEY, null)?.let { DeviceSecret.decrypt(it) }?.let { String(it, Charsets.UTF_8) }
            if (id != null && pem != null) runCatching { Credentials(id, parsePrivateKey(pem)) }.getOrNull() else null
        } else {
            null
        }
        val credentials = own ?: builtIn()
        cached = credentials
        return credentials
    }

    private fun builtIn(): Credentials? {
        if (BuildConfig.ENABLE_BANKING_APPLICATION_ID.isBlank() || BuildConfig.ENABLE_BANKING_PRIVATE_KEY_B64.isBlank()) return null
        val pem = String(Base64.decode(BuildConfig.ENABLE_BANKING_PRIVATE_KEY_B64, Base64.DEFAULT), Charsets.UTF_8)
        return runCatching { Credentials(BuildConfig.ENABLE_BANKING_APPLICATION_ID, parsePrivateKey(pem)) }.getOrNull()
    }

    /** Reads an RSA private key from PEM text — PKCS#8 ("BEGIN PRIVATE KEY", what the Enable
     * Banking Control Panel generates) or PKCS#1 ("BEGIN RSA PRIVATE KEY"). */
    fun parsePrivateKey(pem: String): PrivateKey {
        val isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
        val body = pem.lines().filterNot { it.startsWith("-----") }.joinToString("").replace(Regex("\\s"), "")
        require(body.isNotEmpty() && (pem.contains("PRIVATE KEY"))) { "That file isn't a private key (.pem)." }
        val der = try {
            Base64.decode(body, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("That file isn't a private key (.pem).")
        }
        val pkcs8 = if (isPkcs1) wrapPkcs1(der) else der
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        } catch (e: Exception) {
            throw IllegalArgumentException("That private key couldn't be read.")
        }
    }

    /** PKCS#1 RSAPrivateKey → PKCS#8 PrivateKeyInfo (version 0, rsaEncryption, the key as an
     * OCTET STRING). */
    private fun wrapPkcs1(pkcs1: ByteArray): ByteArray {
        val algorithm = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        )
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val octets = byteArrayOf(0x04) + derLength(pkcs1.size) + pkcs1
        val content = version + algorithm + octets
        return byteArrayOf(0x30) + derLength(content.size) + content
    }

    private fun derLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
        length < 0x10000 -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        else -> byteArrayOf(0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())
    }
}
