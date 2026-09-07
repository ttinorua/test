package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType
import java.util.Calendar
import java.util.TimeZone

private const val SALARY_MAIN_CATEGORY = "income"
private const val SALARY_CATEGORY = "pay, benefits and pension"

/** Matches the bank export's own category for salary/wage payments. */
fun isSalaryCategory(mainCategoryName: String?, categoryName: String?): Boolean {
    return mainCategoryName?.trim()?.lowercase() == SALARY_MAIN_CATEGORY &&
        categoryName?.trim()?.lowercase() == SALARY_CATEGORY
}

/**
 * When [enabled], a transaction categorized as Income / "Pay, benefits and pension"
 * (typically a salary paid on the last day of a month) is attributed to the
 * *following* month for reporting purposes — e.g. income/expense totals and the
 * spending overview. The transaction's own stored date never changes, so the
 * account register and running balance stay tied to the real payment date; only
 * which reporting period it's counted in shifts.
 */
fun effectiveReportingDate(
    date: Long,
    type: TransactionType,
    mainCategoryName: String?,
    categoryName: String?,
    enabled: Boolean
): Long {
    if (!enabled || type != TransactionType.INCOME) return date
    if (!isSalaryCategory(mainCategoryName, categoryName)) return date

    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = date
    cal.add(Calendar.MONTH, 1)
    return cal.timeInMillis
}
