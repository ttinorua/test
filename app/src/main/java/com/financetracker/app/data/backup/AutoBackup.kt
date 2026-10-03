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
import com.financetracker.app.data.backup.cloud.CloudException
import com.financetracker.app.data.backup.cloud.OneDriveBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BackupDestination(val label: String) {
    PHONE("This phone"),
    ONEDRIVE("OneDrive"),
    GOOGLE_DRIVE("Google Drive")
}

data class AutoBackupState(
    val enabled: Boolean = false,
    val destination: BackupDestination? = null,
    /** Where the backup goes, e.g. "OneDrive · name@outlook.com" or a file name on the phone. */
    val locationLabel: String? = null,
    val passwordProtected: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null
)

/**
 * The weekly automatic backup (Settings > Import/Export), to one of three places:
 * - [BackupDestination.PHONE]: a file the user picks once with the system file picker; the app
 *   keeps permission to write it.
 * - [BackupDestination.ONEDRIVE]: the app's own folder in the user's OneDrive, after a Microsoft
 *   sign-in in the browser ([OneDriveBackup]).
 * - [BackupDestination.GOOGLE_DRIVE]: a file the app creates in the user's Google Drive, after
 *   approving access with Google's account picker ([GoogleDriveBackup]).
 *
 * [AutoBackupWorker] replaces that one file with a fresh full backup every week, so it always
 * holds the latest data (OneDrive and Google Drive also keep earlier versions). The backup
 * password and the OneDrive sign-in are kept on this phone encrypted with a key from the Android
 * Keystore, so the worker can run without asking.
 */
object AutoBackupSettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_DESTINATION = "auto_backup_destination"
    private const val KEY_URI = "auto_backup_uri"
    private const val KEY_LOCATION = "auto_backup_location"
    private const val KEY_PASSWORD = "auto_backup_password"
    private const val KEY_LAST_SUCCESS = "auto_backup_last_success"
    private const val KEY_LAST_ERROR = "auto_backup_last_error"
    private const val KEY_ONEDRIVE_REFRESH = "auto_backup_onedrive_refresh"
    private const val KEY_GDRIVE_FILE_ID = "auto_backup_gdrive_file_id"
    private const val KEY_PENDING_PASSWORD = "auto_backup_pending_password"
    private const val KEY_PENDING_STATE = "auto_backup_pending_state"
    private const val KEY_PENDING_VERIFIER = "auto_backup_pending_verifier"

    /** Device-specific (file permissions, Keystore-encrypted secrets, cloud sign-ins), so never
     * part of a backup itself, and kept as they are when a backup is restored. */
    val KEYS = setOf(
        KEY_ENABLED, KEY_DESTINATION, KEY_URI, KEY_LOCATION, KEY_PASSWORD, KEY_LAST_SUCCESS, KEY_LAST_ERROR,
        KEY_ONEDRIVE_REFRESH, KEY_GDRIVE_FILE_ID, KEY_PENDING_PASSWORD, KEY_PENDING_STATE, KEY_PENDING_VERIFIER
    )

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
            destination = destination,
            locationLabel = prefs.getString(KEY_LOCATION, null),
            passwordProtected = prefs.contains(KEY_PASSWORD),
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it > 0 },
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    val destination: BackupDestination?
        get() = prefs.getString(KEY_DESTINATION, null)?.let { name -> BackupDestination.entries.firstOrNull { it.name == name } }
            ?: if (prefs.contains(KEY_URI)) BackupDestination.PHONE else null

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

    /** Backs up weekly to [uri] (a file the user just created with the system picker), keeping
     * write access to it. Throws if the chosen location doesn't allow lasting access. */
    fun enableOnPhone(context: Context, uri: Uri, password: CharArray?) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val oldUri = backupUri?.takeIf { it != uri }
        activate(context, BackupDestination.PHONE, describe(context, uri), password) {
            putString(KEY_URI, uri.toString())
        }
        oldUri?.let { releasePermission(context, it) }
    }

    /** Starts the OneDrive sign-in: remembers the chosen password and the sign-in's PKCE secrets
     * until the browser comes back to [completeOneDrive]. Returns the URL to open. */
    fun beginOneDrive(password: CharArray?): String {
        val pending = OneDriveBackup.startSignIn()
        prefs.edit().apply {
            putString(KEY_PENDING_STATE, pending.state)
            putString(KEY_PENDING_VERIFIER, pending.codeVerifier)
            if (password != null && password.isNotEmpty()) {
                putString(KEY_PENDING_PASSWORD, DeviceSecret.encrypt(String(password).toByteArray(Charsets.UTF_8)))
            } else {
                remove(KEY_PENDING_PASSWORD)
            }
        }.commit()
        return pending.url
    }

    /** Finishes the OneDrive sign-in from the browser redirect. Blocking (network) — call off the
     * main thread. Returns the signed-in account. */
    fun completeOneDrive(context: Context, code: String, state: String?): String? {
        val expectedState = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        if (expectedState == null || verifier == null || state != expectedState) {
            throw CloudException("The OneDrive sign-in expired. Try again from Settings > Import/Export.")
        }
        val tokens = OneDriveBackup.completeSignIn(code, verifier)
        val password = prefs.getString(KEY_PENDING_PASSWORD, null)
            ?.let { DeviceSecret.decrypt(it) }
            ?.let { String(it, Charsets.UTF_8).toCharArray() }
        val label = "OneDrive" + (tokens.account?.let { " · $it" } ?: "")
        val oldUri = backupUri
        activate(context, BackupDestination.ONEDRIVE, label, password) {
            putString(KEY_ONEDRIVE_REFRESH, DeviceSecret.encrypt(tokens.refreshToken.toByteArray(Charsets.UTF_8)))
        }
        password?.fill('\u0000')
        oldUri?.let { releasePermission(context, it) }
        return tokens.account
    }

    fun enableGoogleDrive(context: Context, account: String?, password: CharArray?) {
        val oldUri = backupUri
        activate(context, BackupDestination.GOOGLE_DRIVE, "Google Drive" + (account?.let { " · $it" } ?: ""), password) {}
        oldUri?.let { releasePermission(context, it) }
    }

    private fun activate(
        context: Context,
        destination: BackupDestination,
        location: String,
        password: CharArray?,
        extra: SharedPreferences.Editor.() -> Unit
    ) {
        prefs.edit().apply {
            KEYS.forEach { remove(it) }
            putBoolean(KEY_ENABLED, true)
            putString(KEY_DESTINATION, destination.name)
            putString(KEY_LOCATION, location)
            if (password != null && password.isNotEmpty()) {
                putString(KEY_PASSWORD, DeviceSecret.encrypt(String(password).toByteArray(Charsets.UTF_8)))
            }
            extra()
        }.commit()
        refresh()
        schedule(context)
        backUpNow(context)
    }

    fun oneDriveRefreshToken(): String? =
        prefs.getString(KEY_ONEDRIVE_REFRESH, null)?.let { DeviceSecret.decrypt(it) }?.let { String(it, Charsets.UTF_8) }

    fun saveOneDriveRefreshToken(token: String) {
        prefs.edit().putString(KEY_ONEDRIVE_REFRESH, DeviceSecret.encrypt(token.toByteArray(Charsets.UTF_8))).apply()
    }

    var googleDriveFileId: String?
        get() = prefs.getString(KEY_GDRIVE_FILE_ID, null)
        set(value) { prefs.edit().putString(KEY_GDRIVE_FILE_ID, value).apply() }

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

    // A cloud destination uploads the file, so wait for a connection.
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
        return "$name · this phone"
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
