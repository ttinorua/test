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
    fun `disabled setting never shifts even a matching salary category`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(
            date, TransactionType.INCOME, "Income", "Pay, benefits and pension", enabled = false
        )
        assertEquals(date, result)
    }

    @Test
    fun `matching salary category shifts to next month regardless of day`() {
        val date = utcMillis(2026, 8, 15)
        val result = effectiveReportingDate(
            date, TransactionType.INCOME, "Income", "Pay, benefits and pension", enabled = true
        )
        assertEquals(Triple(2026, 9, 15), yearMonthDay(result))
    }

    @Test
    fun `matching salary category is case-insensitive`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(
            date, TransactionType.INCOME, "INCOME", "PAY, BENEFITS AND PENSION", enabled = true
        )
        assertEquals(Triple(2026, 9, 30), yearMonthDay(result))
    }

    @Test
    fun `other income categories are not shifted`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(
            date, TransactionType.INCOME, "Income", "Other income", enabled = true
        )
        assertEquals(date, result)
    }

    @Test
    fun `expense with the same category strings is never shifted`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(
            date, TransactionType.EXPENSE, "Income", "Pay, benefits and pension", enabled = true
        )
        assertEquals(date, result)
    }

    @Test
    fun `null category never shifts`() {
        val date = utcMillis(2026, 8, 31)
        val result = effectiveReportingDate(date, TransactionType.INCOME, null, null, enabled = true)
        assertEquals(date, result)
    }
}
