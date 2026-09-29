package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import java.util.Calendar
import java.util.TimeZone

/** Categories that recur by habit, not by contract, and so are never anticipated no matter how
 * regularly they repeat — parking at the same garage every workday, buying groceries at the same
 * supermarket, or fuelling up / charging an EV at the same station, isn't a scheduled bill, even
 * though the note+category can look identical for months in a row the same way a real bill does.
 * Applies even if the user marks one of these "Fixe" by mistake — a deliberate hard override, not
 * just the default. */
private val EXCLUDED_CATEGORIES = setOf("parking", "groceries", "fuel")

private enum class Cadence { MONTHLY, QUARTERLY, YEARLY, UNKNOWN }

/** One recurring expense that hasn't posted yet in the month being projected for, projected at
 * its most recent occurrence's amount. [estimatedDate] is that month's predicted posting date —
 * a real prediction (a detected monthly/quarterly/yearly cadence) when [dateIsEstimated] is
 * false, otherwise a rough guess when there's too little history to be confident. [dismissKey] identifies this specific bill for this specific
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

private fun daysBetween(a: Long, b: Long): Long = (b - a) / (24L * 60 * 60 * 1000)

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

/** A bank reference code rather than part of the merchant's name: pure digits ("01978") or a
 * letters+digits code ("P45451234"). Digits-with-punctuation tokens like an account number
 * "1234-5678901" are kept, since those tell different transfer destinations apart. */
private fun isReferenceToken(token: String): Boolean =
    token.any(Char::isDigit) && (token.all(Char::isDigit) || token.any(Char::isLetter))

/** The merchant-identifying words of [note], lowercased — Sydbank threads a running reference
 * (and sometimes a payment code) into every note, e.g. "MCD 01943 Spotify P4545..." vs
 * "MCD 01990 SpotifySE" for the same subscription. Falls back to the whole note if nothing is
 * left. */
private fun identityTokensOf(note: String): List<String> {
    val tokens = note.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return tokens.filterNot(::isReferenceToken).ifEmpty { tokens }
}

private const val MIN_PREFIX_MATCH_LENGTH = 4

/** Same merchant if every word lines up, allowing one to be a prefix of the other ("spotify" vs
 * "spotifyse") so a bank's regional-suffix variants still count as the same bill. */
private fun sameMerchant(a: List<String>, b: List<String>): Boolean =
    a.size == b.size && a.zip(b).all { (x, y) ->
        x == y || (minOf(x.length, y.length) >= MIN_PREFIX_MATCH_LENGTH && (x.startsWith(y) || y.startsWith(x)))
    }

/** One recurring series: same account and same merchant, regardless of category — re-categorizing
 * one month's posting (e.g. Prime Video moved to a different category) must not split the series,
 * or the recategorized posting would never count as the bill having already posted. */
private class RecurringGroup(val accountId: Long, val identity: List<String>) {
    val variants = mutableListOf(identity)
    val txs = mutableListOf<TransactionWithDetails>()
}

/** Groups oldest-first, so a group's [RecurringGroup.identity] (and therefore its dismiss key)
 * stays stable as new postings with slightly different notes arrive. */
private fun groupRecurring(expenses: List<TransactionWithDetails>): List<RecurringGroup> {
    val groups = mutableListOf<RecurringGroup>()
    for (tx in expenses.sortedBy { it.date }) {
        val identity = identityTokensOf(tx.note)
        val group = groups.firstOrNull { g ->
            g.accountId == tx.accountId && g.variants.any { sameMerchant(it, identity) }
        } ?: RecurringGroup(tx.accountId, identity).also { groups += it }
        if (identity !in group.variants) group.variants += identity
        group.txs += tx
    }
    return groups
}

private fun dismissKeyFor(bucket: MonthBucket, group: RecurringGroup): String =
    "${bucket.year}-${bucket.month}|${group.accountId}|${group.identity.joinToString(" ")}"

private data class Qualification(val qualifies: Boolean, val predictedDate: Long, val dateIsEstimated: Boolean)

private fun MonthBucket.index() = year * 12 + month

/** A series under a category the user has marked "Fixe" in Settings (see
 * [com.financetracker.app.data.prefs.FixedExpenseCategories]): its billing cadence (monthly,
 * quarterly, or yearly) is detected from every occurrence on record, and it's anticipated only
 * in a month that cadence lands on. A single occurrence (or an irregular history) assumes
 * monthly. A series whose next due date already passed before [nowBucket] without posting is
 * treated as stopped. Projecting further ahead than the next due date steps forward by cadence,
 * so "Next month" still shows a monthly bill that hasn't posted this month yet either. */
private fun qualifyFixedExpense(
    sortedTxs: List<TransactionWithDetails>,
    nowBucket: MonthBucket,
    targetBucket: MonthBucket
): Qualification {
    val cadence = detectCadence(sortedTxs.map { it.date })
    var predicted = predictNextByCadence(sortedTxs.last().date, cadence)
    if (monthBucketOf(predicted).index() < nowBucket.index()) return Qualification(false, 0L, true)
    while (monthBucketOf(predicted).index() < targetBucket.index()) {
        predicted = predictNextByCadence(predicted, cadence)
    }
    return Qualification(monthBucketOf(predicted) == targetBucket, predicted, cadence == Cadence.UNKNOWN)
}

/**
 * Every recurring expense not yet posted in the month being projected for — [monthsAhead] months
 * after [now]'s own month (0 = this month, 1 = next month, and so on) — whose series (same account
 * and merchant) is currently under a category the user has marked "Fixe" ([fixedCategoryIds], set
 * in Settings > Categories). Nothing outside a Fixe category is ever anticipated: guessing from
 * how often a merchant repeats flagged too many ordinary purchases (restaurants, furniture
 * stores) as bills.
 *
 * Fixe/excluded status and the displayed category come from each series' most recent posting.
 * [EXCLUDED_CATEGORIES] (Parking, Groceries, Fuel) are never anticipated regardless of how often they
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

    val nowBucket = monthBucketOf(now)
    val targetBucket = monthBucketOf(now, monthsAhead)

    return groupRecurring(expenses).mapNotNull { group ->
        val sortedTxs = group.txs
        val lastCategory = (sortedTxs.last().categoryName ?: "Uncategorized").trim().lowercase()
        if (lastCategory in EXCLUDED_CATEGORIES) return@mapNotNull null

        val lastCategoryId = sortedTxs.last().categoryId
        if (lastCategoryId == null || lastCategoryId !in fixedCategoryIds) return@mapNotNull null
        if (sortedTxs.any { monthBucketOf(it.date) == targetBucket }) return@mapNotNull null

        val qualification = qualifyFixedExpense(sortedTxs, nowBucket, targetBucket)
        if (!qualification.qualifies) return@mapNotNull null

        val dismissKey = dismissKeyFor(targetBucket, group)
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
