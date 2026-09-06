package com.financetracker.app.util

import java.util.Calendar
import java.util.TimeZone

enum class PeriodOption(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    CUSTOM("Custom range")
}

/** [start, endExclusive) epoch-millis bounds. For CUSTOM, [customRange] is used verbatim. */
fun periodRange(option: PeriodOption, customRange: Pair<Long, Long>?): Pair<Long, Long> {
    val tz = TimeZone.getTimeZone("UTC")
    val now = Calendar.getInstance(tz)

    fun startOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return when (option) {
        PeriodOption.THIS_MONTH -> {
            val start = startOfDay(now).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            start.timeInMillis to end.timeInMillis
        }
        PeriodOption.LAST_MONTH -> {
            val start = startOfDay(now).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            start.timeInMillis to end.timeInMillis
        }
        PeriodOption.LAST_3_MONTHS -> {
            val end = (startOfDay(now).apply { set(Calendar.DAY_OF_MONTH, 1) }.clone() as Calendar)
                .apply { add(Calendar.MONTH, 1) }
            val start = (end.clone() as Calendar).apply { add(Calendar.MONTH, -3) }
            start.timeInMillis to end.timeInMillis
        }
        PeriodOption.THIS_YEAR -> {
            val start = startOfDay(now).apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
            start.timeInMillis to end.timeInMillis
        }
        PeriodOption.ALL_TIME -> 0L to Long.MAX_VALUE
        PeriodOption.CUSTOM -> customRange ?: (0L to Long.MAX_VALUE)
    }
}
