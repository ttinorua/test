package com.financetracker.app.data.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AutoBackupState(
    val enabled: Boolean = false,
    /** Where the backup file is, e.g. "FinanceTracker-auto-backup.ftbackup · Google Drive". */
    val locationLabel: String? = null,
    val passwordProtected: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null
)

/**
 * The weekly automatic backup (Settings > Import/Export). The user picks a file once — in Google
 * Drive, OneDrive or anywhere else the system file picker offers — and the app keeps permission
 * to write it; [AutoBackupWorker] then overwrites that one file with a fresh full backup every
 * week, so it always holds the latest data (Google Drive and OneDrive keep earlier versions).
 *
 * The backup password, if any, is kept on this phone encrypted with a key from the Android
 * Keystore, so the worker can encrypt without asking.
 */
object AutoBackupSettings {
    private const val PREFS_NAME = "finance_prefs"
    const val KEY_ENABLED = "auto_backup_enabled"
    const val KEY_URI = "auto_backup_uri"
    const val KEY_LOCATION = "auto_backup_location"
    const val KEY_PASSWORD = "auto_backup_password"
    const val KEY_LAST_SUCCESS = "auto_backup_last_success"
    const val KEY_LAST_ERROR = "auto_backup_last_error"

    /** Device-specific (a file permission and a Keystore-encrypted password), so never part of a
     * backup itself. */
    val KEYS = setOf(KEY_ENABLED, KEY_URI, KEY_LOCATION, KEY_PASSWORD, KEY_LAST_SUCCESS, KEY_LAST_ERROR)

    const val FILE_NAME = "FinanceTracker-auto-backup.ftbackup"
    private const val PERIODIC_WORK = "auto_backup_weekly"
    private const val NOW_WORK = "auto_backup_now"

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AutoBackupState())
    val state: StateFlow<AutoBackupState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        refresh()
    }

    private fun refresh() {
        _state.value = AutoBackupState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            locationLabel = prefs.getString(KEY_LOCATION, null),
            passwordProtected = prefs.contains(KEY_PASSWORD),
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it > 0 },
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    val backupUri: Uri? get() = if (::prefs.isInitialized) prefs.getString(KEY_URI, null)?.let(Uri::parse) else null

    /** The backup password: [Password.None] for an unencrypted backup, [Password.Unavailable]
     * when one was set but can no longer be decrypted on this phone. */
    fun password(): Password {
        val stored = prefs.getString(KEY_PASSWORD, null) ?: return Password.None
        val plain = DeviceSecret.decrypt(stored) ?: return Password.Unavailable
        return Password.Set(String(plain, Charsets.UTF_8).toCharArray())
    }

    sealed class Password {
        data object None : Password()
        data object Unavailable : Password()
        class Set(val chars: CharArray) : Password()
    }

    /** Keeps write access to [uri] (a file the user just created with the system picker),
     * schedules the weekly backup and runs the first one right away. Throws if the chosen
     * location doesn't allow lasting access. */
    fun enable(context: Context, uri: Uri, password: CharArray?) {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        backupUri?.takeIf { it != uri }?.let { old -> releasePermission(context, old) }
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, true)
            putString(KEY_URI, uri.toString())
            putString(KEY_LOCATION, describe(context, uri))
            if (password != null && password.isNotEmpty()) {
                putString(KEY_PASSWORD, DeviceSecret.encrypt(String(password).toByteArray(Charsets.UTF_8)))
            } else {
                remove(KEY_PASSWORD)
            }
            remove(KEY_LAST_ERROR)
            remove(KEY_LAST_SUCCESS)
        }.commit()
        refresh()
        schedule(context)
        backUpNow(context)
    }

    fun disable(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
        backupUri?.let { releasePermission(context, it) }
        prefs.edit().apply { KEYS.forEach { remove(it) } }.commit()
        refresh()
    }

    fun backUpNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().setConstraints(constraints()).build()
        )
    }

    fun recordSuccess() {
        prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply()
        refresh()
    }

    fun recordFailure(message: String) {
        prefs.edit().putString(KEY_LAST_ERROR, message).apply()
        refresh()
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    // A cloud location (Google Drive, OneDrive) uploads the file, so wait for a connection.
    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    private fun releasePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun describe(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: FILE_NAME
        val authority = uri.authority.orEmpty()
        val place = when {
            "google.android.apps.docs" in authority -> "Google Drive"
            "skydrive" in authority || "onedrive" in authority.lowercase() -> "OneDrive"
            "com.android.externalstorage" in authority || "downloads" in authority -> "this phone"
            else -> null
        }
        return if (place != null) "$name · $place" else name
    }
}

/** Encrypts small secrets with an AES key that never leaves this phone's Android Keystore. */
internal object DeviceSecret {
    private const val ALIAS = "finance_tracker_auto_backup"
    private const val KEYSTORE = "AndroidKeyStore"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): ByteArray? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        cipher.doFinal(bytes, 12, bytes.size - 12)
    }.getOrNull()
}
