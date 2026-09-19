package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

private const val LOOKBACK_MONTHS = 3
private const val MIN_OCCURRENCES = 2

/** How many months back counts as "recent" for [TRUSTED_MAIN_CATEGORIES]/[TRUSTED_CATEGORIES] —
 * these are trusted as recurring off a single occurrence, but only if it's this recent; an old
 * one-off loan payment from a year ago shouldn't resurrect itself indefinitely. */
private const val TRUSTED_CATEGORY_RECENT_MONTHS = 2

/** How many days apart a recurring bill's historical posting days can be and still count as a
 * known, consistent day of month (e.g. always the 27th-29th) rather than a rough estimate. */
private const val DAY_OF_MONTH_CONSISTENCY_THRESHOLD = 4

/** Main categories that are inherently recurring/contractual in nature — trusted as recurring
 * off a single occurrence within [TRUSTED_CATEGORY_RECENT_MONTHS], unlike everything else, which
 * needs to actually repeat ([MIN_OCCURRENCES] times in [LOOKBACK_MONTHS] months) before it's
 * trusted. */
private val TRUSTED_MAIN_CATEGORIES = setOf("insurance")

/** Individual categories (regardless of main category) with the same single-occurrence trust as
 * [TRUSTED_MAIN_CATEGORIES] — a consumer loan, a credit/loan interest charge, or an electricity
 * bill is a scheduled cost even the first time it's seen, so it doesn't need to repeat to be
 * anticipated. */
private val TRUSTED_CATEGORIES = setOf("interest and fees", "consumer loan", "loan and debt (other)", "electricity")

/** One recurring expense that hasn't posted yet this month, projected at its most recent
 * occurrence's amount. [estimatedDate] is this month's predicted posting date — a real
 * prediction (the historical day of month, when it's been consistent across 2+ occurrences)
 * when [dateIsEstimated] is false, otherwise a rough guess (the most recent occurrence's day of
 * month) when there's only one occurrence to go on, or the history is too irregular to pin down
 * a day with any confidence. [dismissKey] identifies this specific bill for this specific month —
 * pass it to [com.financetracker.app.data.prefs.DismissedRecurringExpenses.dismiss] to remove it
 * from "Upcoming expenses" (and the Dashboard Expenses tile total) until it actually posts or the
 * month rolls over. */
data class AnticipatedExpense(
    val label: String,
    val mainCategory: String,
    val category: String,
    val colorHex: String,
    val amount: Double,
    val estimatedDate: Long,
    val dateIsEstimated: Boolean,
    val dismissKey: String
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

/** The [count] calendar months strictly before [now]'s month — never includes the current month
 * itself, since that's what we're deciding whether to anticipate for. */
private fun priorMonthBuckets(now: Long, count: Int): Set<MonthBucket> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
    return (1..count).map {
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

private fun isTrustedCategory(key: RecurringKey): Boolean =
    key.mainCategory in TRUSTED_MAIN_CATEGORIES || key.category in TRUSTED_CATEGORIES

private fun dismissKeyFor(currentBucket: MonthBucket, key: RecurringKey): String =
    "${currentBucket.year}-${currentBucket.month}|${key.accountId}|${key.mainCategory}|${key.category}|${key.note}"

/**
 * Every recurring expense that hasn't posted yet this month, either:
 * - the same account+category+note appearing in at least [MIN_OCCURRENCES] of the last
 *   [LOOKBACK_MONTHS] calendar months (e.g. a phone bill, a monthly transfer to savings), or
 * - under a category trusted as inherently recurring/contractual ([TRUSTED_MAIN_CATEGORIES]:
 *   Insurance; [TRUSTED_CATEGORIES]: Interest and fees, Consumer loan, Loan and debt (Other),
 *   Electricity), which only needs a single occurrence within the last
 *   [TRUSTED_CATEGORY_RECENT_MONTHS] months to be trusted, since the category itself already
 *   implies a scheduled cost.
 *
 * Settings' "Anticipate recurring bills" toggle adds these to the Dashboard's Expenses tile total
 * (see [anticipatedRecurringExpenseTotal]) and lists them individually in that tile's transaction
 * drill-down, so Remaining reflects what's left once the month's known fixed costs actually go
 * out, not just what's already posted. Only ever projects one month ahead (the current one) —
 * this doesn't attempt to anticipate a bill due next month or later.
 *
 * Each qualifying, not-yet-posted group projects its most recent occurrence's amount (reacts to
 * a real change — a plan upgrade, a rent increase — faster than averaging would). A group that
 * already has a transaction this month is left alone: it's already counted in the real total,
 * adding an estimate on top would double-count it. [dismissedKeys] (see
 * [com.financetracker.app.data.prefs.DismissedRecurringExpenses]) excludes anything the user has
 * explicitly removed from this month's list.
 *
 * [transactions] should already be scoped to whichever account (or all accounts) the caller
 * cares about — this only groups and projects, it doesn't filter by account itself.
 */
fun anticipatedRecurringExpenses(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis(),
    dismissedKeys: Set<String> = emptySet()
): List<AnticipatedExpense> {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return emptyList()

    val currentBucket = monthBucketOf(now)
    val lookback = priorMonthBuckets(now, LOOKBACK_MONTHS)
    val recentForTrustedCategory = priorMonthBuckets(now, TRUSTED_CATEGORY_RECENT_MONTHS)

    return expenses.groupBy { recurringKeyOf(it) }.mapNotNull { (key, txs) ->
        val byMonth = txs.groupBy { monthBucketOf(it.date) }
        if (byMonth.containsKey(currentBucket)) return@mapNotNull null

        val lookbackMonths = byMonth.filterKeys { it in lookback }
        val qualifiesByFrequency = lookbackMonths.size >= MIN_OCCURRENCES
        val qualifiesByTrustedCategory = isTrustedCategory(key) && lookbackMonths.keys.any { it in recentForTrustedCategory }
        if (!qualifiesByFrequency && !qualifiesByTrustedCategory) return@mapNotNull null

        val dismissKey = dismissKeyFor(currentBucket, key)
        if (dismissKey in dismissedKeys) return@mapNotNull null

        val mostRecent = txs.maxBy { it.date }
        val historicalDays = lookbackMonths.values.map { monthTxs -> dayOfMonthOf(monthTxs.maxBy { it.date }.date) }
        val isConsistentDay = historicalDays.size >= 2 && (historicalDays.max() - historicalDays.min()) <= DAY_OF_MONTH_CONSISTENCY_THRESHOLD
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
            dateIsEstimated = !isConsistentDay,
            dismissKey = dismissKey
        )
    }.sortedBy { it.estimatedDate }
}

/** Sum of [anticipatedRecurringExpenses] — what the Dashboard Expenses tile adds on top of what's
 * actually posted. */
fun anticipatedRecurringExpenseTotal(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis(),
    dismissedKeys: Set<String> = emptySet()
): Double = anticipatedRecurringExpenses(transactions, now, dismissedKeys).sumOf { it.amount }
