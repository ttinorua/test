package com.financetracker.app.data.importexport

import com.financetracker.app.data.db.entity.Transaction

/**
 * Re-applies categories from a backup spreadsheet (the app's own export) onto transactions that
 * are already in the app — e.g. after a reinstall, once the bank has re-synced the same history
 * uncategorized or AI-categorized. A backup row matches an existing transaction by the same
 * date/amount/type/note identity import and account-merge use ([DuplicateTransactionFilter.keyOf]),
 * paired one-to-one so identical same-day transactions each get their own backup row.
 *
 * Backup rows that were themselves Uncategorized are ignored, so a restore never wipes a category
 * the app has since assigned.
 */
object CategoryRestore {

    data class Match(val transaction: Transaction, val row: ParsedTransactionRow)

    /** [matches] to apply, and how many categorized backup rows had no transaction to land on
     * (e.g. older history the bank no longer returns, or a manually added transaction). */
    data class Plan(val matches: List<Match>, val unmatchedCount: Int)

    fun plan(existing: List<Transaction>, backupRows: List<ParsedTransactionRow>): Plan {
        val available = existing
            .groupBy { DuplicateTransactionFilter.keyOf(it) }
            .mapValues { (_, txs) -> ArrayDeque(txs.sortedBy { it.id }) }
        val matches = mutableListOf<Match>()
        var unmatched = 0
        for (row in backupRows) {
            if (isUncategorized(row)) continue
            val tx = available[DuplicateTransactionFilter.keyOf(row)]?.removeFirstOrNull()
            if (tx == null) unmatched++ else matches += Match(tx, row)
        }
        return Plan(matches, unmatched)
    }

    private fun isUncategorized(row: ParsedTransactionRow) =
        row.categoryName.isBlank() || row.categoryName.trim().equals("Uncategorized", ignoreCase = true)
}
