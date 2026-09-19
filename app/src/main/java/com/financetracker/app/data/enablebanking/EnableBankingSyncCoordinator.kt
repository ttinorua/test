package com.financetracker.app.data.enablebanking

import com.financetracker.app.data.ai.CategorySuggester
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.BalanceReconciler
import com.financetracker.app.data.importexport.DuplicateTransactionFilter
import com.financetracker.app.data.importexport.ParsedTransactionRow
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.data.repository.FinanceRepository

data class SyncProgress(val done: Int, val total: Int)

/** [remaining] > 0 means the caller stopped early ([EnableBankingSyncCoordinator.syncSelectedAccounts]'s
 * `maxGroups` limit) and more work is left — call again passing this outcome's [totalConsidered]
 * as the next call's `knownTotal` to keep progress counting up instead of resetting. */
data class SyncOutcome(
    val importedCount: Int,
    val skippedCount: Int,
    val failureMessage: String?,
    val totalConsidered: Int,
    val remaining: Int
)

/**
 * Pulls every transaction for the selected linked accounts into the local DB, skipping
 * anything already imported and reconciling each account's balance against the bank's own
 * running total. Shared by the manual "Sync now" button and the automatic sync-on-app-open so
 * both paths behave identically instead of drifting apart.
 *
 * Enable Banking always returns an account's *full* history on every fetch (there's no
 * incremental "since date" support at the API level) — the app relies entirely on comparing
 * against what's already saved locally ([DuplicateTransactionFilter]) to know what's actually
 * new. Day to day that's only a handful of transactions, but the very first sync (or any sync
 * after local data was wiped, e.g. an app reinstall) treats the *entire* history as new, and
 * each unique merchant needs its own sequential AI categorization call — the same cost as the
 * AI categorization backfill. This is designed to be called repeatedly in bounded batches
 * ([maxGroups] merchant groups at a time, across however many accounts that spans) rather than
 * run to completion in one call, the same way [com.financetracker.app.data.ai.AiCategorizationCoordinator]
 * is, so a large first sync survives Android's ~10 minute execution ceiling for background work.
 * Each batch's new transactions are inserted immediately (never held in memory until some later
 * point), so an interrupted batch keeps whatever it already imported — the next call's
 * duplicate check naturally excludes it and picks up with whatever's left, never re-importing or
 * re-categorizing it. A given account's balance is only reconciled once every one of its rows
 * for this run has been processed without hitting the batch limit.
 */
object EnableBankingSyncCoordinator {

    private data class PendingAccount(
        val accountId: Long,
        val bankAccount: LinkedBankAccount,
        val rows: List<ParsedTransactionRow>,
        val uniqueGroups: List<List<ParsedTransactionRow>>
    )

    /** Returns null if there's no active connection or no accounts selected — the two "nothing
     * to do" cases that mean don't bother calling the API at all. */
    suspend fun syncSelectedAccounts(
        repository: FinanceRepository,
        maxGroups: Int = Int.MAX_VALUE,
        knownTotal: Int? = null,
        onProgress: suspend (SyncProgress) -> Unit = {}
    ): SyncOutcome? {
        if (EnableBankingPrefs.sessionId.value == null) return null
        val accountsToSync = EnableBankingPrefs.linkedAccounts.value
            .filter { it.uid in EnableBankingPrefs.selectedAccountUids.value }
        if (accountsToSync.isEmpty()) return null

        var totalSkipped = 0
        var failure: String? = null
        val pending = mutableListOf<PendingAccount>()
        for (bankAccount in accountsToSync) {
            val accountId = resolveLocalAccount(repository, bankAccount)

            val rowsResult = EnableBankingService.fetchTransactions(bankAccount.uid, sinceEpochMillis = null)
            if (rowsResult.isFailure) {
                failure = "Couldn't sync \"${bankAccount.name}\": ${describeError(rowsResult.exceptionOrNull()!!)}"
                continue
            }
            val rows = rowsResult.getOrThrow()

            val existing = repository.getTransactionsForAccount(accountId)
            val filterResult = DuplicateTransactionFilter.filter(existing, rows)
            totalSkipped += filterResult.duplicateCount

            val groups = filterResult.uniqueRows.groupBy { it.note.trim().lowercase() }.values.toList()
            pending += PendingAccount(accountId, bankAccount, rows, groups)
        }

        val targetsCount = pending.sumOf { acct -> acct.uniqueGroups.sumOf { it.size } }
        if (targetsCount == 0) {
            if (failure == null) EnableBankingPrefs.setLastSyncedAt(System.currentTimeMillis())
            return SyncOutcome(0, totalSkipped, failure, knownTotal ?: 0, 0)
        }

        val total = knownTotal ?: targetsCount
        val alreadyDone = total - targetsCount

        val categories = repository.getCategories()
        // One cache per call, keyed by normalized note, so repeated merchants (very common
        // across a real transaction history) only cost one AI call each instead of one per row.
        val suggestionCache = mutableMapOf<String, Category?>()

        var totalImported = 0
        var doneThisRun = 0
        var groupsProcessed = 0
        onProgress(SyncProgress(alreadyDone, total))

        outer@ for (acct in pending) {
            for (group in acct.uniqueGroups) {
                if (groupsProcessed >= maxGroups) break@outer
                groupsProcessed++

                val suggested = suggestCategory(group.first().note, categories, suggestionCache)
                val uncategorizedCache = mutableMapOf<TransactionType, Long>()
                val transactions = group.map { row ->
                    val categoryId = suggested?.id ?: uncategorizedCache.getOrPut(row.type) {
                        repository.getOrCreateCategory(row.mainCategoryName, row.categoryName, row.type).id
                    }
                    Transaction(
                        amount = row.amount,
                        type = row.type,
                        accountId = acct.accountId,
                        categoryId = categoryId,
                        date = row.date,
                        note = row.note
                    )
                }
                repository.addTransactions(transactions)
                totalImported += transactions.size
                doneThisRun += group.size
                onProgress(SyncProgress(alreadyDone + doneThisRun, total))
            }
            // Only reached when every group of this account was processed above without hitting
            // the batch limit — an account left partially done gets reconciled on a later call,
            // once its own remaining groups are drained the same way.
            BalanceReconciler.reconcile(repository, acct.accountId, acct.rows, sourceOrderIsNewestFirst = false)
        }

        val remaining = targetsCount - doneThisRun
        if (remaining == 0 && failure == null) {
            EnableBankingPrefs.setLastSyncedAt(System.currentTimeMillis())
        }
        return SyncOutcome(totalImported, totalSkipped, failure, total, remaining)
    }

    /** Sydbank reuses the same product label (e.g. "Privatkonto") across more than one real
     * account, so the label alone isn't a safe local-account key — always disambiguate with a
     * suffix that's actually unique per account (the IBAN, falling back to the account uid).
     *
     * Once a bank uid has been resolved to a local account once, that mapping is persisted
     * ([EnableBankingPrefs.accountLinkMap]) and reused directly on every later sync — so if the
     * user renames the account afterward in Settings, sync keeps finding the same account by its
     * stored id instead of re-deriving the name and failing to match, which would otherwise
     * create a duplicate account. The name match below only runs as a one-time bootstrap (a
     * fresh link, or an install from before this mapping existed) and immediately persists the
     * id it finds so it's never needed again for that uid. */
    private suspend fun resolveLocalAccount(repository: FinanceRepository, bankAccount: LinkedBankAccount): Long {
        val accounts = repository.getAccounts()
        val linkedId = EnableBankingPrefs.accountLinkMap.value[bankAccount.uid]
        if (linkedId != null && accounts.any { it.id == linkedId }) return linkedId

        val name = localAccountName(bankAccount)
        val existing = accounts.firstOrNull { it.name == name }
        val accountId = existing?.id
            ?: repository.upsertAccount(Account(name = name, currencyCode = bankAccount.currency))
        EnableBankingPrefs.setAccountLink(bankAccount.uid, accountId)
        return accountId
    }

    private fun localAccountName(bankAccount: LinkedBankAccount): String {
        val label = bankAccount.product ?: "Account"
        val suffix = bankAccount.iban?.takeLast(4) ?: bankAccount.uid.take(6)
        return "Sydbank $label ••$suffix"
    }

    /** Enable Banking sends no category data at all, so every new transaction is AI-suggested
     * against the user's real categories when Claude is configured — silently skipped (falls
     * back to Uncategorized) otherwise, since this is a background sync, not a user action. */
    private suspend fun suggestCategory(
        note: String,
        categories: List<Category>,
        cache: MutableMap<String, Category?>
    ): Category? {
        if (!ClaudeService.isConfigured) return null
        val key = note.trim().lowercase()
        if (key.isBlank()) return null
        if (cache.containsKey(key)) return cache.getValue(key)
        val match = CategorySuggester.suggest(note, categories).getOrNull()
        cache[key] = match
        return match
    }
}
