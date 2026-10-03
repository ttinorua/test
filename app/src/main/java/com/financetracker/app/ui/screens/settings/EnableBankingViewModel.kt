package com.financetracker.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.financetracker.app.data.bank.Bank
import com.financetracker.app.data.bank.BankCategories
import com.financetracker.app.data.bank.SupportedBanks
import com.financetracker.app.data.enablebanking.ENABLE_BANKING_REDIRECT_URL
import com.financetracker.app.data.enablebanking.EnableBankingCredentials
import com.financetracker.app.data.enablebanking.EnableBankingCredentialsState
import com.financetracker.app.data.enablebanking.EnableBankingService
import com.financetracker.app.data.enablebanking.EnableBankingSyncWorker
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.prefs.BankConnection
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

private data class PrefsSnapshot(
    val connections: List<BankConnection>,
    val lastSyncedAt: Long?,
    val selectedBankId: String,
    val credentials: EnableBankingCredentialsState
)

private data class SyncState(val isSyncing: Boolean, val progress: SyncProgressUi?)

data class SyncProgressUi(val done: Int, val total: Int)

/** One connected bank as the UI sees it — [bankId]/[bankDisplayName] so the screen never has to
 * re-derive a bank's name from whichever one happens to be globally selected elsewhere. */
data class BankConnectionUi(
    val bankId: String,
    val bankDisplayName: String,
    val linkedAccounts: List<LinkedBankAccount>,
    val selectedAccountUids: Set<String>,
    val consentValidUntil: Long?
)

data class EnableBankingUiState(
    val isConfigured: Boolean = false,
    /** The user's own Enable Banking registration, or null when using the app's built-in one. */
    val ownApplicationId: String? = null,
    val connections: List<BankConnectionUi> = emptyList(),
    /** Banks [SupportedBanks.ALL] doesn't already have a connection for — what the "connect
     * another bank" picker offers. */
    val connectableBanks: List<Bank> = SupportedBanks.ALL,
    val lastSyncedAt: Long? = null,
    val isStartingAuth: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgressUi? = null,
    val statusMessage: String? = null,
    val authUrl: String? = null,
    val selectedBankId: String = SupportedBanks.DEFAULT.id
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
            EnableBankingPrefs.connections,
            EnableBankingPrefs.lastSyncedAt,
            EnableBankingPrefs.selectedBankId,
            EnableBankingCredentials.state
        ) { connections, lastSyncedAt, selectedBankId, credentials ->
            PrefsSnapshot(connections, lastSyncedAt, selectedBankId, credentials)
        },
        combine(isSyncing, syncProgress) { syncing, progress -> SyncState(syncing, progress) },
        _isStartingAuth,
        _statusMessage,
        _authUrl
    ) { prefs, syncState, isStartingAuth, statusMessage, authUrl ->
        val connectedIds = prefs.connections.map { it.bankId }.toSet()
        EnableBankingUiState(
            isConfigured = prefs.credentials.isConfigured,
            ownApplicationId = prefs.credentials.ownApplicationId,
            connections = prefs.connections.map { connection ->
                BankConnectionUi(
                    bankId = connection.bankId,
                    bankDisplayName = SupportedBanks.byId(connection.bankId).displayName,
                    linkedAccounts = connection.linkedAccounts,
                    selectedAccountUids = connection.selectedAccountUids,
                    consentValidUntil = connection.consentValidUntil
                )
            },
            connectableBanks = SupportedBanks.ALL.filterNot { it.id in connectedIds },
            lastSyncedAt = prefs.lastSyncedAt,
            isStartingAuth = isStartingAuth,
            isSyncing = syncState.isSyncing,
            syncProgress = syncState.progress,
            statusMessage = statusMessage,
            authUrl = authUrl,
            selectedBankId = prefs.selectedBankId
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

    /** Starts a consent flow for [bankId]. The screen observes [EnableBankingUiState.authUrl]
     * and opens it in the browser, then calls [consumeAuthUrl].
     *
     * Serves two purposes depending on whether [bankId] already has a connection: connecting a
     * bank for the first time, or — while already connected — "Refresh accounts" for that same
     * bank, no need to [disconnect] it first. [EnableBankingPrefs.saveConnection] (which the
     * redirect eventually calls into via [EnableBankingService.completeAuth]) only ever touches
     * [bankId]'s own connection: every other connected bank is left exactly as it was, and within
     * this one, the sync selection for accounts already known is preserved while whatever's
     * newly returned is auto-selected — so refreshing is always safe to pick up an account opened
     * at the bank since the last connect. */
    fun startBankAuth(bankId: String) {
        if (_isStartingAuth.value) return
        _isStartingAuth.value = true
        _statusMessage.value = null
        viewModelScope.launch {
            EnableBankingService.startAuth(ENABLE_BANKING_REDIRECT_URL, SupportedBanks.byId(bankId))
                .onSuccess { authStart -> _authUrl.value = authStart.url }
                .onFailure { e -> _statusMessage.value = "Couldn't start bank login: ${describeError(e)}" }
            _isStartingAuth.value = false
        }
    }

    fun consumeAuthUrl() {
        _authUrl.value = null
    }

    /** Changes which bank the "connect another bank" picker currently has selected — only
     * matters before calling [startBankAuth] for a brand-new bank. Also seeds that bank's own
     * starter categories right away, rather than waiting for the next app launch. */
    fun selectBank(id: String) {
        EnableBankingPrefs.setSelectedBankId(id)
        viewModelScope.launch {
            BankCategories.ensure(repository, SupportedBanks.byId(id))
        }
    }

    fun setAccountSelected(bankId: String, uid: String, selected: Boolean) {
        EnableBankingPrefs.setAccountSelected(bankId, uid, selected)
    }

    /** Disconnects only [bankId] — every other connected bank keeps syncing. The sync worker is
     * only cancelled if this was the last connection left, since it otherwise still has other
     * banks' accounts to sync. */
    fun disconnect(bankId: String) {
        EnableBankingService.disconnect(bankId)
        if (EnableBankingPrefs.connections.value.isEmpty()) {
            workManager.cancelUniqueWork(EnableBankingSyncWorker.UNIQUE_WORK_NAME)
            EnableBankingSyncWorker.clearPersistedState(appContext)
        }
        _statusMessage.value = null
    }

    fun dismissStatusMessage() {
        _statusMessage.value = null
    }

    /** Pulls every transaction every connected bank makes available for every selected linked
     * account, across every connection at once. Runs through [EnableBankingSyncWorker], shared
     * with the automatic sync-on-app-open so both paths behave identically. */
    fun syncNow() {
        if (isSyncing.value) return
        val anyAccountSelected = EnableBankingPrefs.connections.value.any { it.selectedAccountUids.isNotEmpty() }
        if (!anyAccountSelected) {
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
