package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.util.effectiveReportingDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SalaryShiftTest {

    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day)
        return cal.timeInMillis
    }

    private fun yearMonthDay(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `disabled setting never shifts the date`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(date, TransactionType.INCOME, enabled = false)
        assertEquals(date, result)
    }

    @Test
    fun `expense near month end is never shifted even when enabled`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(date, TransactionType.EXPENSE, enabled = true)
        assertEquals(date, result)
    }

    @Test
    fun `income on the last day of a 31-day month shifts to next month`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(date, TransactionType.INCOME, enabled = true)
        assertEquals(Triple(2026, 9, 30), yearMonthDay(result))
    }

    @Test
    fun `income three days before month end (Feb, 28-day month) shifts to next month`() {
        val date = utcMillis(2026, 2, 26)
        val result = effectiveReportingDate(date, TransactionType.INCOME, enabled = true)
        assertEquals(Triple(2026, 3, 26), yearMonthDay(result))
    }

    @Test
    fun `income mid-month is not shifted`() {
        val date = utcMillis(2026, 8, 15)
        val result = effectiveReportingDate(date, TransactionType.INCOME, enabled = true)
        assertEquals(date, result)
    }

    @Test
    fun `income four days before month end is not shifted`() {
        val date = utcMillis(2026, 8, 27)
        val result = effectiveReportingDate(date, TransactionType.INCOME, enabled = true)
        assertEquals(date, result)
    }
}
