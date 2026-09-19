package com.financetracker.app.data.enablebanking

import com.financetracker.app.data.ai.CategorySuggester
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.BalanceReconciler
import com.financetracker.app.data.importexport.DuplicateTransactionFilter
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.data.repository.FinanceRepository

data class SyncOutcome(val importedCount: Int, val skippedCount: Int, val failureMessage: String?)

/**
 * Pulls every transaction for the selected linked accounts into the local DB, skipping
 * anything already imported and reconciling each account's balance against the bank's own
 * running total. Shared by the manual "Sync now" button and the automatic sync-on-app-open so
 * both paths behave identically instead of drifting apart.
 */
object EnableBankingSyncCoordinator {

    /** Returns null if there's no active connection or no accounts selected — the two "nothing
     * to do" cases that mean don't bother calling the API at all. */
    suspend fun syncSelectedAccounts(repository: FinanceRepository): SyncOutcome? {
        if (EnableBankingPrefs.sessionId.value == null) return null
        val accountsToSync = EnableBankingPrefs.linkedAccounts.value
            .filter { it.uid in EnableBankingPrefs.selectedAccountUids.value }
        if (accountsToSync.isEmpty()) return null

        val categories = repository.getCategories()
        // One cache per sync run, keyed by normalized note, so repeated merchants (very common
        // across a real transaction history) only cost one AI call each instead of one per row.
        val suggestionCache = mutableMapOf<String, Category?>()

        var totalImported = 0
        var totalSkipped = 0
        var failure: String? = null

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

            val uncategorizedCache = mutableMapOf<TransactionType, Long>()
            val transactions = filterResult.uniqueRows.map { row ->
                val suggested = suggestCategory(row.note, categories, suggestionCache)
                val categoryId = suggested?.id ?: uncategorizedCache.getOrPut(row.type) {
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
            BalanceReconciler.reconcile(repository, accountId, rows, sourceOrderIsNewestFirst = false)

            totalImported += transactions.size
            totalSkipped += filterResult.duplicateCount
        }

        EnableBankingPrefs.setLastSyncedAt(System.currentTimeMillis())
        return SyncOutcome(totalImported, totalSkipped, failure)
    }

    /** Sydbank reuses the same product label (e.g. "Privatkonto") across more than one real
     * account, so the label alone isn't a safe local-account key — always disambiguate with a
     * suffix that's actually unique per account (the IBAN, falling back to the account uid). */
    private suspend fun resolveLocalAccount(repository: FinanceRepository, bankAccount: LinkedBankAccount): Long {
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
