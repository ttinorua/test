package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import java.util.Calendar
import java.util.TimeZone

private const val LOOKBACK_MONTHS = 3
private const val MIN_OCCURRENCES = 2

private data class MonthBucket(val year: Int, val month: Int)

private fun monthBucketOf(date: Long): MonthBucket {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
    return MonthBucket(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
}

/** The [LOOKBACK_MONTHS] calendar months strictly before [now]'s month — never includes the
 * current month itself, since that's what we're deciding whether to anticipate for. */
private fun lookbackBuckets(now: Long): Set<MonthBucket> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
    return (1..LOOKBACK_MONTHS).map {
        cal.add(Calendar.MONTH, -1)
        MonthBucket(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }.toSet()
}

/** Same identity a recurring bill/transfer keeps month to month: the account it's paid from,
 * its category, and its (normalized) note — e.g. "Checking / Media / phone, internet, streaming
 * and tv / telia" every month. Deliberately not amount-based, since a bill can legitimately vary
 * (electricity by season) while still being the same recurring commitment. */
private data class RecurringKey(val accountId: Long, val mainCategory: String, val category: String, val note: String)

private fun recurringKeyOf(tx: TransactionWithDetails) = RecurringKey(
    accountId = tx.accountId,
    mainCategory = (tx.mainCategoryName ?: "Uncategorized").trim().lowercase(),
    category = (tx.categoryName ?: "Uncategorized").trim().lowercase(),
    note = tx.note.trim().lowercase()
)

/**
 * Sum of recurring expenses — the same account+category+note appearing in at least
 * [MIN_OCCURRENCES] of the last [LOOKBACK_MONTHS] calendar months (e.g. a phone bill, a
 * utility, a monthly transfer to savings) — that haven't posted yet this month. Settings'
 * "Anticipate recurring bills" toggle adds this to the Dashboard's Expenses tile so Remaining
 * reflects what's left once the month's known fixed costs actually go out, not just what's
 * already posted.
 *
 * Each qualifying, not-yet-posted group projects its most recent occurrence's amount (reacts to
 * a real change — a plan upgrade, a rent increase — faster than averaging would). A group that
 * already has a transaction this month is left alone: it's already counted in the real total,
 * adding an estimate on top would double-count it.
 *
 * [transactions] should already be scoped to whichever account (or all accounts) the caller
 * cares about — this only groups and projects, it doesn't filter by account itself.
 */
fun anticipatedRecurringExpenseTotal(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis()
): Double {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return 0.0

    val currentBucket = monthBucketOf(now)
    val lookback = lookbackBuckets(now)

    var total = 0.0
    expenses.groupBy { recurringKeyOf(it) }.forEach { (_, txs) ->
        val byMonth = txs.groupBy { monthBucketOf(it.date) }
        if (byMonth.keys.count { it in lookback } < MIN_OCCURRENCES) return@forEach
        if (byMonth.containsKey(currentBucket)) return@forEach

        total += txs.maxBy { it.date }.amount
    }
    return total
}
