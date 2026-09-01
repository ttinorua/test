package com.financetracker.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Formatters {
    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun currency(amount: Double): String = currencyFormat.format(amount)
    fun date(epochMillis: Long): String = dateFormat.format(epochMillis)
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
