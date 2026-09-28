package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionWithDetails

/**
 * Offered after a manual category edit: [similar] transactions that look like the same
 * recurring payee/expense as the one just edited, not yet filed under [newCategoryName]
 * ([newCategoryId]) themselves.
 */
data class SimilarTransactionsPrompt(
    val newCategoryId: Long?,
    val newCategoryName: String,
    val similar: List<TransactionWithDetails>
)

/**
 * Other transactions that look like the same real-world payee/expense as [edited] — same
 * [TransactionWithDetails.type], not already filed under [newCategoryId], and either an
 * identical (trimmed, case-insensitive) note or an identical amount. Searches across every
 * transaction the caller passes in, not just whatever period/filter happens to be on screen,
 * since a bill from three months ago is just as "similar" as one from this week.
 */
fun findSimilarTransactions(
    transactions: List<TransactionWithDetails>,
    edited: TransactionWithDetails,
    newCategoryId: Long?
): List<TransactionWithDetails> {
    val note = edited.note.trim().lowercase()
    return transactions.filter { tx ->
        tx.id != edited.id &&
            tx.type == edited.type &&
            tx.categoryId != newCategoryId &&
            ((note.isNotEmpty() && tx.note.trim().lowercase() == note) || tx.amount == edited.amount)
    }
}
