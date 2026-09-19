package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

private const val LOOKBACK_MONTHS = 3
private const val MIN_OCCURRENCES = 2

/** How many days apart a recurring bill's historical posting days can be and still count as a
 * known, consistent day of month (e.g. always the 27th-29th) rather than a rough estimate. */
private const val DAY_OF_MONTH_CONSISTENCY_THRESHOLD = 4

/** One recurring expense that hasn't posted yet this month, projected at its most recent
 * occurrence's amount. [estimatedDate] is this month's predicted posting date — a real
 * prediction (the historical day of month, when it's been consistent) when [dateIsEstimated] is
 * false, otherwise a rough guess (the most recent occurrence's day of month) when the history is
 * too irregular to pin down a day with any confidence. */
data class AnticipatedExpense(
    val label: String,
    val mainCategory: String,
    val category: String,
    val colorHex: String,
    val amount: Double,
    val estimatedDate: Long,
    val dateIsEstimated: Boolean
)

private data class MonthBucket(val year: Int, val month: Int)

private fun monthBucketOf(date: Long): MonthBucket {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
    return MonthBucket(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
}

private fun dayOfMonthOf(date: Long): Int {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
    return cal.get(Calendar.DAY_OF_MONTH)
}

/** [now]'s own month, with the day of month set to [day] (clamped to that month's real length,
 * e.g. day 31 in a 30-day month lands on the 30th). */
private fun dateForDayInCurrentMonth(now: Long, day: Int): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, lastDayOfMonth))
    return cal.timeInMillis
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
 * Every recurring expense — the same account+category+note appearing in at least
 * [MIN_OCCURRENCES] of the last [LOOKBACK_MONTHS] calendar months (e.g. a phone bill, a
 * utility, a monthly transfer to savings) — that hasn't posted yet this month. Settings'
 * "Anticipate recurring bills" toggle adds these to the Dashboard's Expenses tile total (see
 * [anticipatedRecurringExpenseTotal]) and lists them individually in that tile's transaction
 * drill-down, so Remaining reflects what's left once the month's known fixed costs actually go
 * out, not just what's already posted.
 *
 * Each qualifying, not-yet-posted group projects its most recent occurrence's amount (reacts to
 * a real change — a plan upgrade, a rent increase — faster than averaging would). A group that
 * already has a transaction this month is left alone: it's already counted in the real total,
 * adding an estimate on top would double-count it.
 *
 * [transactions] should already be scoped to whichever account (or all accounts) the caller
 * cares about — this only groups and projects, it doesn't filter by account itself.
 */
fun anticipatedRecurringExpenses(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis()
): List<AnticipatedExpense> {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return emptyList()

    val currentBucket = monthBucketOf(now)
    val lookback = lookbackBuckets(now)

    return expenses.groupBy { recurringKeyOf(it) }.mapNotNull { (_, txs) ->
        val byMonth = txs.groupBy { monthBucketOf(it.date) }
        val lookbackMonths = byMonth.filterKeys { it in lookback }
        if (lookbackMonths.size < MIN_OCCURRENCES) return@mapNotNull null
        if (byMonth.containsKey(currentBucket)) return@mapNotNull null

        val mostRecent = txs.maxBy { it.date }
        val historicalDays = lookbackMonths.values.map { monthTxs -> dayOfMonthOf(monthTxs.maxBy { it.date }.date) }
        val isConsistentDay = (historicalDays.max() - historicalDays.min()) <= DAY_OF_MONTH_CONSISTENCY_THRESHOLD
        val predictedDay = if (isConsistentDay) {
            historicalDays.average().roundToInt()
        } else {
            dayOfMonthOf(mostRecent.date)
        }

        AnticipatedExpense(
            label = mostRecent.note.ifBlank { mostRecent.categoryName ?: "Recurring expense" },
            mainCategory = mostRecent.mainCategoryName ?: "Uncategorized",
            category = mostRecent.categoryName ?: "Uncategorized",
            colorHex = mostRecent.categoryColorHex ?: "#9E9E9E",
            amount = mostRecent.amount,
            estimatedDate = dateForDayInCurrentMonth(now, predictedDay),
            dateIsEstimated = !isConsistentDay
        )
    }.sortedBy { it.estimatedDate }
}

/** Sum of [anticipatedRecurringExpenses] — what the Dashboard Expenses tile adds on top of what's
 * actually posted. */
fun anticipatedRecurringExpenseTotal(transactions: List<TransactionWithDetails>, now: Long = System.currentTimeMillis()): Double =
    anticipatedRecurringExpenses(transactions, now).sumOf { it.amount }
