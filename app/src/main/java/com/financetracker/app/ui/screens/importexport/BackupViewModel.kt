package com.financetracker.app.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.backup.AutoBackupSettings
import com.financetracker.app.data.backup.BackupCodec
import com.financetracker.app.data.backup.BackupContents
import com.financetracker.app.data.backup.BackupPasswordRequiredException
import com.financetracker.app.data.backup.FullBackup
import com.financetracker.app.data.backup.WrongBackupPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A backup file read and (if needed) unlocked, waiting for the user to confirm the restore. */
data class PendingRestore(
    val createdAt: Long,
    val transactionCount: Int,
    val accountCount: Int,
    val includesBankConnections: Boolean
)

data class BackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    /** An encrypted backup was picked; ask for its password. */
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val pendingRestore: PendingRestore? = null,
    val restored: Boolean = false
)

class BackupViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var createPassword: CharArray? = null
    private var pickedBytes: ByteArray? = null
    private var decoded: BackupContents? = null

    /** Remembers the password (empty for none) for the backup file the user is about to pick a
     * location for. */
    fun prepareBackup(password: CharArray) {
        createPassword?.fill('\u0000')
        createPassword = password.takeIf { it.isNotEmpty() }
    }

    fun writeBackup(uri: Uri) {
        val password = createPassword
        createPassword = null
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val result = runCatching { withContext(Dispatchers.IO) { FullBackup.writeTo(context, uri, password) } }
            password?.fill('\u0000')
            _uiState.update { state ->
                state.copy(
                    isWorking = false,
                    message = result.fold(
                        onSuccess = { contents ->
                            "Backup saved: ${contents.transactions.size} transactions, " +
                                "${contents.accounts.size} accounts" +
                                if (contents.includesBankConnections) ", with your bank connections." else "."
                        },
                        onFailure = { "Backup failed: ${it.message}" }
                    )
                )
            }
        }
    }

    fun openBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Couldn't open the file.")
                }
            }.getOrElse { e ->
                _uiState.update { it.copy(isWorking = false, message = "Couldn't read the backup: ${e.message}") }
                return@launch
            }
            pickedBytes = bytes
            decode(null)
        }
    }

    fun unlockBackup(password: CharArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, wrongPassword = false) }
            decode(password)
            password.fill('\u0000')
        }
    }

    private suspend fun decode(password: CharArray?) {
        val bytes = pickedBytes ?: return
        val result = runCatching { withContext(Dispatchers.Default) { BackupCodec.decode(bytes, password) } }
        result.onSuccess { contents ->
            decoded = contents
            _uiState.update {
                it.copy(
                    isWorking = false,
                    needsPassword = false,
                    wrongPassword = false,
                    pendingRestore = PendingRestore(
                        createdAt = contents.createdAt,
                        transactionCount = contents.transactions.size,
                        accountCount = contents.accounts.size,
                        includesBankConnections = contents.includesBankConnections
                    )
                )
            }
        }.onFailure { e ->
            when (e) {
                is BackupPasswordRequiredException -> _uiState.update { it.copy(isWorking = false, needsPassword = true) }
                is WrongBackupPasswordException ->
                    _uiState.update { it.copy(isWorking = false, needsPassword = true, wrongPassword = true) }
                else -> {
                    clearPicked()
                    _uiState.update { it.copy(isWorking = false, message = e.message ?: "Couldn't read the backup.") }
                }
            }
        }
    }

    fun confirmRestore() {
        val contents = decoded ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, pendingRestore = null) }
            val result = runCatching { withContext(Dispatchers.IO) { FullBackup.restore(context, contents) } }
            clearPicked()
            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(isWorking = false, restored = true) },
                    onFailure = { e -> state.copy(isWorking = false, message = "Restore failed: ${e.message}") }
                )
            }
        }
    }

    fun cancelRestore() {
        clearPicked()
        _uiState.update { it.copy(needsPassword = false, wrongPassword = false, pendingRestore = null) }
    }

    fun restartApp() = FullBackup.restartApp(context)

    /** Turns on the weekly backup to [uri], a file the user just created on the phone,
     * protected with [password] (empty for none). */
    fun enableAutoBackupOnPhone(uri: Uri, password: CharArray) {
        val result = runCatching { AutoBackupSettings.enableOnPhone(context, uri, password.takeIf { it.isNotEmpty() }) }
        password.fill('\u0000')
        result.onFailure {
            _uiState.update { it.copy(message = "This location doesn't allow automatic backups. Choose another folder.") }
        }
    }

    /** Remembers [password] and returns the Microsoft sign-in page to open in the browser; the
     * browser comes back to the app (MainActivity), which finishes turning the backup on. */
    fun beginOneDriveSignIn(password: CharArray): String {
        val url = AutoBackupSettings.beginOneDrive(password.takeIf { it.isNotEmpty() })
        password.fill('\u0000')
        return url
    }

    fun enableAutoBackupToGoogleDrive(account: String?, password: CharArray) {
        AutoBackupSettings.enableGoogleDrive(context, account, password.takeIf { it.isNotEmpty() })
        password.fill('\u0000')
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun disableAutoBackup() = AutoBackupSettings.disable(context)

    fun autoBackupNow() {
        AutoBackupSettings.backUpNow(context)
        _uiState.update { it.copy(message = "Backing up in the background…") }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun clearPicked() {
        pickedBytes = null
        decoded = null
    }

    override fun onCleared() {
        createPassword?.fill('\u0000')
    }
}
