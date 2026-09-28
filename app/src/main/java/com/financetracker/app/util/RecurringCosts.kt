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

/** Categories that recur by habit, not by contract, and so are never anticipated no matter how
 * regularly they repeat — parking at the same garage every workday, or buying groceries at the
 * same supermarket, isn't a scheduled bill, even though the note+category can look identical for
 * months in a row the same way a real bill does. Applies even if the user marks one of these
 * "Fixe" by mistake — a deliberate hard override, not just the default. */
private val EXCLUDED_CATEGORIES = setOf("parking", "groceries")

private enum class Cadence { MONTHLY, QUARTERLY, YEARLY, UNKNOWN }

/** One recurring expense that hasn't posted yet in the month being projected for, projected at
 * its most recent occurrence's amount. [estimatedDate] is that month's predicted posting date —
 * a real prediction (a consistent historical day of month, or a detected monthly/quarterly/
 * yearly cadence) when [dateIsEstimated] is false, otherwise a rough guess when there's too
 * little history to be confident. [dismissKey] identifies this specific bill for this specific
 * projected month — pass it to
 * [com.financetracker.app.data.prefs.DismissedRecurringExpenses.dismiss] to remove it from
 * "Upcoming expenses" (and the Dashboard Expenses tile total) until it actually posts or that
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

private fun utcCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

/** [date]'s own calendar month, [monthsOffset] months forward (0 = the month [date] falls in). */
private fun monthBucketOf(date: Long, monthsOffset: Int = 0): MonthBucket {
    val cal = utcCalendar().apply {
        timeInMillis = date
        if (monthsOffset != 0) add(Calendar.MONTH, monthsOffset)
    }
    return MonthBucket(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
}

private fun dayOfMonthOf(date: Long): Int {
    val cal = utcCalendar().apply { timeInMillis = date }
    return cal.get(Calendar.DAY_OF_MONTH)
}

private fun daysBetween(a: Long, b: Long): Long = (b - a) / (24L * 60 * 60 * 1000)

/** [bucket]'s own month, with the day of month set to [day] (clamped to that month's real
 * length, e.g. day 31 in a 30-day month lands on the 30th). */
private fun dateForDayInBucket(bucket: MonthBucket, day: Int): Long {
    val cal = utcCalendar().apply {
        clear()
        set(bucket.year, bucket.month, 1, 0, 0, 0)
    }
    val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, lastDayOfMonth))
    return cal.timeInMillis
}

/** The [count] calendar months strictly before [now]'s month — never includes the current month
 * itself, since that's what we're deciding whether a bill is still "recent" relative to. */
private fun priorMonthBuckets(now: Long, count: Int): Set<MonthBucket> {
    val cal = utcCalendar().apply { timeInMillis = now }
    return (1..count).map {
        cal.add(Calendar.MONTH, -1)
        MonthBucket(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }.toSet()
}

/** The typical gap between [sortedDates]' consecutive entries, classified into a cadence a
 * bill/premium commonly follows. Needs at least 2 dates to say anything at all. */
private fun detectCadence(sortedDates: List<Long>): Cadence {
    if (sortedDates.size < 2) return Cadence.UNKNOWN
    val gaps = sortedDates.zipWithNext { a, b -> daysBetween(a, b) }.sorted()
    val medianGap = gaps[gaps.size / 2]
    return when {
        medianGap in 20..45 -> Cadence.MONTHLY
        medianGap in 75..105 -> Cadence.QUARTERLY
        medianGap in 300..400 -> Cadence.YEARLY
        else -> Cadence.UNKNOWN
    }
}

/** The next date [cadence] predicts after [lastDate] — calendar-unit arithmetic (not a fixed day
 * count), so a monthly bill lands on the same day next month regardless of month length, and a
 * yearly one isn't thrown off by leap years. [Cadence.UNKNOWN] conservatively assumes monthly,
 * the safest default when the history is too irregular to classify (including a category with
 * only a single occurrence on record, which can't have a detectable cadence at all). */
private fun predictNextByCadence(lastDate: Long, cadence: Cadence): Long {
    val cal = utcCalendar().apply { timeInMillis = lastDate }
    when (cadence) {
        Cadence.MONTHLY, Cadence.UNKNOWN -> cal.add(Calendar.MONTH, 1)
        Cadence.QUARTERLY -> cal.add(Calendar.MONTH, 3)
        Cadence.YEARLY -> cal.add(Calendar.YEAR, 1)
    }
    return cal.timeInMillis
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

private fun dismissKeyFor(bucket: MonthBucket, key: RecurringKey): String =
    "${bucket.year}-${bucket.month}|${key.accountId}|${key.mainCategory}|${key.category}|${key.note}"

private data class Qualification(val qualifies: Boolean, val predictedDate: Long, val dateIsEstimated: Boolean)

/** A category the user has marked "Fixe" in Settings (see
 * [com.financetracker.app.data.prefs.FixedExpenseCategories]) is trusted as a scheduled cost
 * without needing to actually repeat first — its real billing cadence (monthly, quarterly, or
 * yearly) is detected from every occurrence on record, and it's only anticipated in the one
 * month that cadence predicts next, never every month by default the way a flat "always assume
 * monthly" rule would. A single occurrence (no cadence to detect yet) falls back to assuming
 * monthly, the same conservative default an irregular history gets. */
private fun qualifyFixedExpense(sortedTxs: List<TransactionWithDetails>, targetBucket: MonthBucket): Qualification {
    val cadence = detectCadence(sortedTxs.map { it.date })
    val last = sortedTxs.last()
    val predicted = predictNextByCadence(last.date, cadence)
    return Qualification(monthBucketOf(predicted) == targetBucket, predicted, cadence == Cadence.UNKNOWN)
}

/** Everything not marked "Fixe": needs to have actually repeated — present in at least
 * [MIN_OCCURRENCES] of the last [LOOKBACK_MONTHS] real months — before it's trusted as recurring
 * at all. */
private fun qualifyByFrequency(
    sortedTxs: List<TransactionWithDetails>,
    byMonth: Map<MonthBucket, List<TransactionWithDetails>>,
    now: Long,
    targetBucket: MonthBucket
): Qualification {
    val lookbackMonths = byMonth.filterKeys { it in priorMonthBuckets(now, LOOKBACK_MONTHS) }
    if (lookbackMonths.size < MIN_OCCURRENCES) return Qualification(false, 0L, true)

    val last = sortedTxs.last()
    val historicalDays = lookbackMonths.values.map { monthTxs -> dayOfMonthOf(monthTxs.maxBy { it.date }.date) }
    val isConsistentDay = (historicalDays.max() - historicalDays.min()) <= DAY_OF_MONTH_CONSISTENCY_THRESHOLD
    val predictedDay = if (isConsistentDay) historicalDays.average().roundToInt() else dayOfMonthOf(last.date)
    return Qualification(true, dateForDayInBucket(targetBucket, predictedDay), !isConsistentDay)
}

/**
 * Every recurring expense not yet posted in the month being projected for — [monthsAhead] months
 * after [now]'s own month (0 = this month, 1 = next month, and so on) — either:
 * - under a category the user has marked "Fixe" ([fixedCategoryIds], set in Settings > Categories
 *   — see [com.financetracker.app.data.prefs.FixedExpenseCategories]), whose actual billing
 *   cadence (monthly, quarterly, or yearly) is detected from its full history and only
 *   anticipated in the one month that predicts next; or
 * - the same account+category+note appearing in at least [MIN_OCCURRENCES] of the last
 *   [LOOKBACK_MONTHS] real calendar months (e.g. a phone bill, a monthly transfer to savings),
 *   for anything not marked Fixe.
 *
 * [EXCLUDED_CATEGORIES] (Parking, Groceries) are never anticipated regardless of how often they
 * repeat, or even if marked Fixe — they recur by habit, not by contract, so the same
 * note+category showing up in back-to-back months doesn't mean a bill is coming due.
 *
 * Settings' "Anticipate recurring bills" toggle adds these to the Dashboard's Expenses tile total
 * (see [anticipatedRecurringExpenseTotal]) for whichever month is currently selected — This month
 * or Next month — and lists them individually in that tile's transaction drill-down, so Remaining
 * reflects what's left (or, a month ahead, what's expected) once known fixed costs actually go
 * out, not just what's already posted.
 *
 * Each qualifying, not-yet-posted group projects its most recent occurrence's amount (reacts to
 * a real change — a plan upgrade, a rent increase — faster than averaging would). A group that
 * already has a transaction in the projected month is left alone: it's already counted in the
 * real total there, adding an estimate on top would double-count it. [dismissedKeys] (see
 * [com.financetracker.app.data.prefs.DismissedRecurringExpenses]) excludes anything the user has
 * explicitly removed from that month's list.
 *
 * [transactions] should already be scoped to whichever account (or all accounts) the caller
 * cares about — this only groups and projects, it doesn't filter by account itself.
 */
fun anticipatedRecurringExpenses(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis(),
    monthsAhead: Int = 0,
    dismissedKeys: Set<String> = emptySet(),
    fixedCategoryIds: Set<Long> = emptySet()
): List<AnticipatedExpense> {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return emptyList()

    val targetBucket = monthBucketOf(now, monthsAhead)

    return expenses.groupBy { recurringKeyOf(it) }.mapNotNull { (key, txs) ->
        if (key.category in EXCLUDED_CATEGORIES) return@mapNotNull null

        val sortedTxs = txs.sortedBy { it.date }
        val byMonth = sortedTxs.groupBy { monthBucketOf(it.date) }
        if (byMonth.containsKey(targetBucket)) return@mapNotNull null

        val lastCategoryId = sortedTxs.last().categoryId
        val isFixed = lastCategoryId != null && lastCategoryId in fixedCategoryIds
        val qualification = if (isFixed) {
            qualifyFixedExpense(sortedTxs, targetBucket)
        } else {
            qualifyByFrequency(sortedTxs, byMonth, now, targetBucket)
        }
        if (!qualification.qualifies) return@mapNotNull null

        val dismissKey = dismissKeyFor(targetBucket, key)
        if (dismissKey in dismissedKeys) return@mapNotNull null

        val mostRecent = sortedTxs.last()
        AnticipatedExpense(
            label = mostRecent.note.ifBlank { mostRecent.categoryName ?: "Recurring expense" },
            mainCategory = mostRecent.mainCategoryName ?: "Uncategorized",
            category = mostRecent.categoryName ?: "Uncategorized",
            colorHex = mostRecent.categoryColorHex ?: "#9E9E9E",
            amount = mostRecent.amount,
            estimatedDate = qualification.predictedDate,
            dateIsEstimated = qualification.dateIsEstimated,
            dismissKey = dismissKey
        )
    }.sortedBy { it.estimatedDate }
}

/** Sum of [anticipatedRecurringExpenses] — what the Dashboard Expenses tile adds on top of what's
 * actually posted. */
fun anticipatedRecurringExpenseTotal(
    transactions: List<TransactionWithDetails>,
    now: Long = System.currentTimeMillis(),
    monthsAhead: Int = 0,
    dismissedKeys: Set<String> = emptySet(),
    fixedCategoryIds: Set<Long> = emptySet()
): Double = anticipatedRecurringExpenses(transactions, now, monthsAhead, dismissedKeys, fixedCategoryIds).sumOf { it.amount }
