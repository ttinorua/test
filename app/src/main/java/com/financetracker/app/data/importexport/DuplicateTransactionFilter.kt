package com.financetracker.app.data.importexport

import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType

/**
 * Filters spreadsheet rows down to ones that aren't already present, so re-importing an
 * overlapping or previously-uploaded file never adds the same transaction twice.
 */
object DuplicateTransactionFilter {

    data class Result(val uniqueRows: List<ParsedTransactionRow>, val duplicateCount: Int)

    /**
     * A row is a duplicate of an existing transaction if its date, amount, type and note all
     * match — matched by count, not just presence, so genuinely repeated same-day, same-amount
     * transactions (e.g. two identical coffees) aren't wrongly treated as duplicates of each
     * other: if two such rows already exist and the file has two more, only the extra ones (if
     * any) beyond that count are kept.
     */
    fun filter(existing: List<Transaction>, rows: List<ParsedTransactionRow>): Result {
        val remainingCounts = existing
            .groupingBy { keyOf(it.date, it.amount, it.type, it.note) }
            .eachCount()
            .toMutableMap()

        val unique = mutableListOf<ParsedTransactionRow>()
        var duplicateCount = 0
        for (row in rows) {
            val key = keyOf(row.date, row.amount, row.type, row.note)
            val remaining = remainingCounts[key] ?: 0
            if (remaining > 0) {
                remainingCounts[key] = remaining - 1
                duplicateCount++
            } else {
                unique.add(row)
            }
        }
        return Result(unique, duplicateCount)
    }

    private data class Key(val date: Long, val amount: Double, val type: TransactionType, val note: String)

    private fun keyOf(date: Long, amount: Double, type: TransactionType, note: String) =
        Key(date, amount, type, note.trim().lowercase())
}
