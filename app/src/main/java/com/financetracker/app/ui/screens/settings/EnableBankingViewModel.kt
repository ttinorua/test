package com.financetracker.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.enablebanking.EnableBankingService
import com.financetracker.app.data.importexport.BalanceReconciler
import com.financetracker.app.data.importexport.DuplicateTransactionFilter
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
private const val SYNC_LOOKBACK_DAYS = 90L

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

    /** Pulls the last [SYNC_LOOKBACK_DAYS] of transactions for every selected linked account,
     * skips anything already imported (same dedup rule as spreadsheet import), and reconciles
     * each account's balance against the bank's own running total. */
    fun syncNow() {
        if (_isSyncing.value) return
        val accountsToSync = EnableBankingPrefs.linkedAccounts.value
            .filter { it.uid in EnableBankingPrefs.selectedAccountUids.value }
        if (accountsToSync.isEmpty()) {
            _statusMessage.value = "Select at least one account to sync."
            return
        }

        _isSyncing.value = true
        _statusMessage.value = null
        viewModelScope.launch {
            var totalImported = 0
            var totalSkipped = 0
            var failure: String? = null

            for (bankAccount in accountsToSync) {
                val accountId = resolveLocalAccount(bankAccount)
                val sinceEpochMillis = System.currentTimeMillis() - SYNC_LOOKBACK_DAYS * 24 * 60 * 60 * 1000

                val rowsResult = EnableBankingService.fetchTransactions(bankAccount.uid, sinceEpochMillis)
                if (rowsResult.isFailure) {
                    failure = "Couldn't sync \"${bankAccount.name}\": ${describeError(rowsResult.exceptionOrNull()!!)}"
                    continue
                }
                val rows = rowsResult.getOrThrow()

                val existing = repository.getTransactionsForAccount(accountId)
                val filterResult = DuplicateTransactionFilter.filter(existing, rows)

                val categoryCache = mutableMapOf<TransactionType, Long>()
                val transactions = filterResult.uniqueRows.map { row ->
                    val categoryId = categoryCache.getOrPut(row.type) {
                        repository.getOrCreateCategory(row.mainCategoryName, row.categoryName, row.type).id
                    }
                    Transaction(
                        amount = row.amount,
                        type = row.type,
                        accountId = accountId,
                        categoryId = categoryId,
                        date = row.date,
                        note = row.note
                    )
                }
                if (transactions.isNotEmpty()) {
                    repository.addTransactions(transactions)
                }
                BalanceReconciler.reconcile(repository, accountId, rows)

                totalImported += transactions.size
                totalSkipped += filterResult.duplicateCount
            }

            EnableBankingPrefs.setLastSyncedAt(System.currentTimeMillis())
            _statusMessage.value = failure
                ?: "Synced: $totalImported new transaction(s), $totalSkipped already up to date."
            _isSyncing.value = false
        }
    }

    /** Sydbank reuses the same product label (e.g. "Privatkonto") across more than one real
     * account, so the label alone isn't a safe local-account key — always disambiguate with a
     * suffix that's actually unique per account (the IBAN, falling back to the account uid). */
    private suspend fun resolveLocalAccount(bankAccount: LinkedBankAccount): Long {
        val name = localAccountName(bankAccount)
        val existing = repository.getAccounts().firstOrNull { it.name == name }
        if (existing != null) return existing.id
        return repository.upsertAccount(Account(name = name, currencyCode = bankAccount.currency))
    }

    private fun localAccountName(bankAccount: LinkedBankAccount): String {
        val label = bankAccount.product ?: "Account"
        val suffix = bankAccount.iban?.takeLast(4) ?: bankAccount.uid.take(6)
        return "Sydbank $label ••$suffix"
    }
}
