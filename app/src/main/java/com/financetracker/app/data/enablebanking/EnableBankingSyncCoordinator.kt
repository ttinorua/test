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
 * every unique merchant needs an AI categorization suggestion — the same cost as the AI
 * categorization backfill. Merchants are AI-suggested [CategorySuggester.BATCH_SIZE] at a time
 * in a single Claude call (see [CategorySuggester.suggestBatch], deduped by note across
 * accounts) instead of one sequential call per merchant, the main lever on how long a large sync
 * actually takes wall-clock. This is also designed to be called repeatedly in bounded batches
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

        // Flatten this call's groups across every pending account, capped at maxGroups, before
        // asking the AI anything — this lets AI suggestions be batched (and deduped by note
        // across accounts, the same merchant can appear in more than one) instead of one
        // sequential Claude call per merchant, the main lever on how long this actually takes.
        data class Work(val acct: PendingAccount, val group: List<ParsedTransactionRow>)
        val work = mutableListOf<Work>()
        outer@ for (acct in pending) {
            for (group in acct.uniqueGroups) {
                if (work.size >= maxGroups) break@outer
                work += Work(acct, group)
            }
        }

        val noteByKey = LinkedHashMap<String, String>()
        for (w in work) {
            val key = w.group.first().note.trim().lowercase()
            noteByKey.putIfAbsent(key, w.group.first().note)
        }
        val suggestionByKey = mutableMapOf<String, Category?>()
        if (ClaudeService.isConfigured) {
            val keys = noteByKey.keys.toList()
            for (chunk in keys.chunked(CategorySuggester.BATCH_SIZE)) {
                val notes = chunk.map { noteByKey.getValue(it) }
                val matches = CategorySuggester.suggestBatch(notes, categories).getOrNull()
                chunk.forEachIndexed { i, key -> suggestionByKey[key] = matches?.getOrNull(i) }
            }
        }

        var totalImported = 0
        var doneThisRun = 0
        val processedGroupsByAccount = mutableMapOf<Long, Int>()
        onProgress(SyncProgress(alreadyDone, total))

        for (w in work) {
            val key = w.group.first().note.trim().lowercase()
            val suggested = suggestionByKey[key]
            val uncategorizedCache = mutableMapOf<TransactionType, Long>()
            val transactions = w.group.map { row ->
                val categoryId = suggested?.id ?: uncategorizedCache.getOrPut(row.type) {
                    repository.getOrCreateCategory(row.mainCategoryName, row.categoryName, row.type).id
                }
                Transaction(
                    amount = row.amount,
                    type = row.type,
                    accountId = w.acct.accountId,
                    categoryId = categoryId,
                    date = row.date,
                    note = row.note
                )
            }
            repository.addTransactions(transactions)
            totalImported += transactions.size
            doneThisRun += w.group.size
            processedGroupsByAccount[w.acct.accountId] = (processedGroupsByAccount[w.acct.accountId] ?: 0) + 1
            onProgress(SyncProgress(alreadyDone + doneThisRun, total))
        }

        // Only reconcile an account whose every group (this call's flattened work list) was
        // actually processed above — one left partially done gets reconciled on a later call,
        // once its own remaining groups are drained the same way.
        for (acct in pending) {
            if ((processedGroupsByAccount[acct.accountId] ?: 0) == acct.uniqueGroups.size) {
                BalanceReconciler.reconcile(repository, acct.accountId, acct.rows, sourceOrderIsNewestFirst = false)
            }
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
}
