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
    private val monthOnlyFormat = SimpleDateFormat("MMMM", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    // Deliberately no explicit UTC timezone (unlike the calendar-day formats above) — this
    // formats a real wall-clock instant (when a bank sync finished), so it should read in
    // whatever timezone the device itself is set to.
    private val syncTimestampFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)

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

    /** A short label for a chart bar: 950, 2.4k, 26.5k, 1.2M. */
    fun compact(value: Double): String {
        val abs = kotlin.math.abs(value)
        val sign = if (value < 0) "-" else ""
        fun oneDecimal(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
        return when {
            abs < 1_000 -> sign + String.format(Locale.US, "%.0f", abs)
            abs < 1_000_000 -> sign + oneDecimal(abs / 1_000) + "k"
            else -> sign + oneDecimal(abs / 1_000_000) + "M"
        }
    }

    fun currencySymbol(currencyCode: String): String = CURRENCY_SYMBOLS[currencyCode] ?: currencyCode

    fun date(epochMillis: Long): String = dateFormat.format(epochMillis)
    fun fullDate(epochMillis: Long): String = fullDateFormat.format(epochMillis)
    fun month(epochMillis: Long): String = monthOnlyFormat.format(epochMillis)
    fun syncTimestamp(epochMillis: Long): String = syncTimestampFormat.format(epochMillis)
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
