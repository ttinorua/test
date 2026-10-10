package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import kotlin.math.abs

/**
 * Finds the bank transaction a receipt belongs to: an expense of the same amount (within 50 øre)
 * booked from a day before to three days after the purchase — banks often book card payments a
 * day or two later. Closest date first; a transaction whose text names the store wins a tie.
 */
object ReceiptMatcher {
    private const val DAY = 86_400_000L
    private const val AMOUNT_TOLERANCE = 0.5

    fun candidates(
        total: Double,
        purchasedAt: Long,
        storeName: String,
        transactions: List<TransactionWithDetails>,
        alreadyMatched: Set<Long>
    ): List<TransactionWithDetails> {
        val store = storeName.lowercase().split(Regex("[^\\p{L}0-9]+")).firstOrNull { it.length >= 3 }
        return transactions
            .filter { tx ->
                tx.type == TransactionType.EXPENSE && !ReceiptSplits.isPart(tx.id) && tx.id !in alreadyMatched &&
                    abs(tx.amount - total) <= AMOUNT_TOLERANCE &&
                    tx.date >= startOfDay(purchasedAt) - DAY && tx.date < startOfDay(purchasedAt) + 4 * DAY
            }
            .sortedWith(
                compareBy<TransactionWithDetails> { abs(startOfDay(it.date) - startOfDay(purchasedAt)) / DAY }
                    .thenBy { if (store != null && it.note.lowercase().contains(store)) 0 else 1 }
                    .thenBy { abs(it.amount - total) }
            )
    }

    /** The match to make without asking: the best candidate, unless another one is just as good. */
    fun best(
        total: Double,
        purchasedAt: Long,
        storeName: String,
        transactions: List<TransactionWithDetails>,
        alreadyMatched: Set<Long>
    ): TransactionWithDetails? {
        val list = candidates(total, purchasedAt, storeName, transactions, alreadyMatched)
        val first = list.firstOrNull() ?: return null
        val second = list.getOrNull(1) ?: return first
        val sameDay = startOfDay(first.date) == startOfDay(second.date)
        val sameAmount = abs(first.amount - second.amount) < 0.01
        return if (sameDay && sameAmount && first.note == second.note) null else first
    }

    private fun startOfDay(t: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = t
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
