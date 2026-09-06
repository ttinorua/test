package com.financetracker.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Formatters {
    // Plain grouped-decimal style (comma thousands, dot decimal) regardless of the
    // selected currency, matching the reference bank app's transaction list.
    private val numberFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val fullDateFormat = SimpleDateFormat("EEEE d. MMMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val monthOnlyFormat = SimpleDateFormat("MMMM", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val CURRENCY_SYMBOLS = mapOf(
        "DKK" to "kr.",
        "NOK" to "kr.",
        "SEK" to "kr.",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£"
    )

    /** Plain formatted number, no currency unit — used where the amount already implies context. */
    fun amount(value: Double): String = numberFormat.format(value)

    /** Formatted number with the given currency's unit appended. */
    fun currency(value: Double, currencyCode: String): String {
        return "${numberFormat.format(value)} ${currencySymbol(currencyCode)}"
    }

    fun currencySymbol(currencyCode: String): String = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode

    fun date(epochMillis: Long): String = dateFormat.format(epochMillis)
    fun fullDate(epochMillis: Long): String = fullDateFormat.format(epochMillis)
    fun month(epochMillis: Long): String = monthOnlyFormat.format(epochMillis)
    fun currentMonthLabel(): String = monthFormat.format(System.currentTimeMillis())
}

/** Start (inclusive) / end (exclusive) epoch-millis bounds for the current UTC calendar month. */
fun currentMonthRange(): Pair<Long, Long> {
    val tz = TimeZone.getTimeZone("UTC")
    val start = Calendar.getInstance(tz).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis to end.timeInMillis
}

fun todayUtcMidnight(): Long {
    val tz = TimeZone.getTimeZone("UTC")
    val cal = Calendar.getInstance(tz).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
