package com.financetracker.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.financetracker.app.data.enablebanking.EnableBankingService
import com.financetracker.app.data.enablebanking.EnableBankingSyncWorker
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

private data class SyncState(val isSyncing: Boolean, val progress: SyncProgressUi?)

data class SyncProgressUi(val done: Int, val total: Int)

data class EnableBankingUiState(
    val isConfigured: Boolean = false,
    val isConnected: Boolean = false,
    val linkedAccounts: List<LinkedBankAccount> = emptyList(),
    val selectedAccountUids: Set<String> = emptySet(),
    val consentValidUntil: Long? = null,
    val lastSyncedAt: Long? = null,
    val isStartingAuth: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgressUi? = null,
    val statusMessage: String? = null,
    val authUrl: String? = null
)

class EnableBankingViewModel(private val repository: FinanceRepository, private val appContext: Context) : ViewModel() {

    private val _isStartingAuth = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _authUrl = MutableStateFlow<String?>(null)

    private val workManager = WorkManager.getInstance(appContext)

    private val syncWorkInfo: StateFlow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow(EnableBankingSyncWorker.UNIQUE_WORK_NAME)
        // Prefer whichever entry is actually still active over just taking the list's first
        // entry — see the equivalent categorization-worker fix for why that matters.
        .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val isSyncing: StateFlow<Boolean> = syncWorkInfo
        .map { it != null && !it.state.isFinished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val syncProgress: StateFlow<SyncProgressUi?> = syncWorkInfo
        .map { info ->
            if (info == null || info.state.isFinished) return@map null
            val done = info.progress.getInt(EnableBankingSyncWorker.KEY_DONE, -1)
            val total = info.progress.getInt(EnableBankingSyncWorker.KEY_TOTAL, -1)
            // total 0 means "still fetching from the bank, not a real total yet" (see
            // EnableBankingSyncCoordinator) — still surfaced as progress (done, with total 0)
            // so the UI can show that a long fetch phase is actually moving, not stuck.
            if (done >= 0 && total >= 0) SyncProgressUi(done, total) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        combine(isSyncing, syncProgress) { syncing, progress -> SyncState(syncing, progress) },
        _isStartingAuth,
        _statusMessage,
        _authUrl
    ) { prefs, syncState, isStartingAuth, statusMessage, authUrl ->
        EnableBankingUiState(
            isConfigured = EnableBankingService.isConfigured,
            isConnected = prefs.sessionId != null,
            linkedAccounts = prefs.linkedAccounts,
            selectedAccountUids = prefs.selectedAccountUids,
            consentValidUntil = prefs.consentValidUntil,
            lastSyncedAt = prefs.lastSyncedAt,
            isStartingAuth = isStartingAuth,
            isSyncing = syncState.isSyncing,
            syncProgress = syncState.progress,
            statusMessage = statusMessage,
            authUrl = authUrl
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EnableBankingUiState(isConfigured = EnableBankingService.isConfigured)
    )

    init {
        viewModelScope.launch {
            syncWorkInfo.collect { info ->
                if (info == null || !info.state.isFinished) return@collect
                _statusMessage.value = when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val imported = info.outputData.getInt(EnableBankingSyncWorker.KEY_IMPORTED, 0)
                        val skipped = info.outputData.getInt(EnableBankingSyncWorker.KEY_SKIPPED, 0)
                        val failure = info.outputData.getString(EnableBankingSyncWorker.KEY_FAILURE)
                        if (!failure.isNullOrBlank()) {
                            failure
                        } else {
                            "Synced: $imported new transaction(s), $skipped already up to date."
                        }
                    }
                    WorkInfo.State.FAILED -> "Sync failed. Try again."
                    else -> _statusMessage.value
                }
                // Otherwise a finished run would keep reappearing every time this ViewModel is
                // recreated (e.g. reopening Settings), since WorkManager keeps finished work
                // around until pruned.
                workManager.pruneWork()
            }
        }
    }

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
        workManager.cancelUniqueWork(EnableBankingSyncWorker.UNIQUE_WORK_NAME)
        EnableBankingSyncWorker.clearPersistedState(appContext)
        EnableBankingService.disconnect()
        _statusMessage.value = null
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }

    /** Pulls every transaction the bank makes available for every selected linked account. Runs
     * through [EnableBankingSyncWorker], shared with the automatic sync-on-app-open so both
     * paths behave identically. */
    fun syncNow() {
        if (isSyncing.value) return
        if (EnableBankingPrefs.selectedAccountUids.value.isEmpty()) {
            _statusMessage.value = "Select at least one account to sync."
            return
        }

        _statusMessage.value = null
        EnableBankingSyncWorker.clearPersistedState(appContext)
        // REPLACE (not KEEP): this call only ever happens from here, guarded by the isSyncing
        // check above, so it can't race with a currently-running worker — it's a deliberate,
        // explicit restart, always giving a clean single work item instead of risking reuse of a
        // leftover/stuck one from a previous run.
        workManager.enqueueUniqueWork(
            EnableBankingSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            EnableBankingSyncWorker.buildRequest()
        )
    }

    /** Escape hatch for a run that's stuck — cancels whatever's registered under this unique
     * work name and clears its persisted total, so the next sync is guaranteed a clean start. */
    fun cancelSync() {
        workManager.cancelUniqueWork(EnableBankingSyncWorker.UNIQUE_WORK_NAME)
        EnableBankingSyncWorker.clearPersistedState(appContext)
        _statusMessage.value = null
    }
}
