package com.financetracker.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.enablebanking.EnableBankingService
import com.financetracker.app.data.enablebanking.EnableBankingSyncCoordinator
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val REDIRECT_URL = "https://ttinorua.github.io/enablebanking-redirect/"

private data class PrefsSnapshot(
    val sessionId: String?,
    val linkedAccounts: List<LinkedBankAccount>,
    val selectedAccountUids: Set<String>,
    val consentValidUntil: Long?,
    val lastSyncedAt: Long?
)

data class EnableBankingUiState(
    val isConfigured: Boolean = false,
    val isConnected: Boolean = false,
    val linkedAccounts: List<LinkedBankAccount> = emptyList(),
    val selectedAccountUids: Set<String> = emptySet(),
    val consentValidUntil: Long? = null,
    val lastSyncedAt: Long? = null,
    val isStartingAuth: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val authUrl: String? = null
)

class EnableBankingViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _isStartingAuth = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _authUrl = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EnableBankingUiState> = combine(
        combine(
            EnableBankingPrefs.sessionId,
            EnableBankingPrefs.linkedAccounts,
            EnableBankingPrefs.selectedAccountUids,
            EnableBankingPrefs.consentValidUntil,
            EnableBankingPrefs.lastSyncedAt
        ) { sessionId, linkedAccounts, selectedAccountUids, consentValidUntil, lastSyncedAt ->
            PrefsSnapshot(sessionId, linkedAccounts, selectedAccountUids, consentValidUntil, lastSyncedAt)
        },
        _isStartingAuth,
        _isSyncing,
        _statusMessage,
        _authUrl
    ) { prefs, isStartingAuth, isSyncing, statusMessage, authUrl ->
        EnableBankingUiState(
            isConfigured = EnableBankingService.isConfigured,
            isConnected = prefs.sessionId != null,
            linkedAccounts = prefs.linkedAccounts,
            selectedAccountUids = prefs.selectedAccountUids,
            consentValidUntil = prefs.consentValidUntil,
            lastSyncedAt = prefs.lastSyncedAt,
            isStartingAuth = isStartingAuth,
            isSyncing = isSyncing,
            statusMessage = statusMessage,
            authUrl = authUrl
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EnableBankingUiState(isConfigured = EnableBankingService.isConfigured)
    )

    /** Starts a new consent flow. The screen observes [EnableBankingUiState.authUrl] and opens
     * it in the browser, then calls [consumeAuthUrl]. */
    fun connect() {
        if (_isStartingAuth.value) return
        _isStartingAuth.value = true
        _statusMessage.value = null
        viewModelScope.launch {
            EnableBankingService.startAuth(REDIRECT_URL)
                .onSuccess { authStart -> _authUrl.value = authStart.url }
                .onFailure { e -> _statusMessage.value = "Couldn't start bank login: ${describeError(e)}" }
            _isStartingAuth.value = false
        }
    }

    fun consumeAuthUrl() {
        _authUrl.value = null
    }

    fun setAccountSelected(uid: String, selected: Boolean) {
        val current = EnableBankingPrefs.selectedAccountUids.value
        EnableBankingPrefs.setSelectedAccountUids(if (selected) current + uid else current - uid)
    }

    fun disconnect() {
        EnableBankingService.disconnect()
        _statusMessage.value = null
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }

    /** Pulls every transaction the bank makes available for every selected linked account. Runs
     * through [EnableBankingSyncCoordinator], shared with the automatic sync-on-app-open so both
     * paths behave identically. */
    fun syncNow() {
        if (_isSyncing.value) return
        if (EnableBankingPrefs.selectedAccountUids.value.isEmpty()) {
            _statusMessage.value = "Select at least one account to sync."
            return
        }

        _isSyncing.value = true
        _statusMessage.value = null
        viewModelScope.launch {
            val outcome = EnableBankingSyncCoordinator.syncSelectedAccounts(repository)
            _statusMessage.value = outcome?.failureMessage
                ?: outcome?.let { "Synced: ${it.importedCount} new transaction(s), ${it.skippedCount} already up to date." }
            _isSyncing.value = false
        }
    }
}
