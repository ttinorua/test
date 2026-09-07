package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import java.util.Calendar
import java.util.TimeZone

private const val MONTH_END_WINDOW_DAYS = 3

/**
 * When [enabled], an income transaction landing in the last few days of a month
 * (a common salary payday) is attributed to the *following* month for reporting
 * purposes — e.g. income/expense totals and the spending overview. The
 * transaction's own stored date never changes, so the account register and
 * running balance stay tied to the real payment date; only which reporting
 * period it's counted in shifts.
 */
fun effectiveReportingDate(date: Long, type: TransactionType, enabled: Boolean): Long {
    if (!enabled || type != TransactionType.INCOME) return date

    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = date
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (day <= lastDayOfMonth - MONTH_END_WINDOW_DAYS) return date

    cal.add(Calendar.MONTH, 1)
    return cal.timeInMillis
}
