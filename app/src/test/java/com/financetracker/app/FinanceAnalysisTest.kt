package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.advisor.FinanceAnalysis
import com.financetracker.app.util.advisor.FinanceCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FinanceAnalysisTest {

    private fun utc(year: Int, month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day)
    }.timeInMillis

    private fun tx(date: Long, amount: Double, note: String, type: TransactionType = TransactionType.EXPENSE) =
        TransactionWithDetails(
            id = 0, amount = amount, type = type, accountId = 1, accountName = "Main",
            categoryId = 1, categoryName = "Streaming", mainCategoryName = "Media",
            categoryColorHex = null, date = date, note = note
        )

    private val now = utc(2026, 10, 3)

    @Test
    fun `a monthly subscription with a price increase is found`() {
        val txs = listOf(
            tx(utc(2026, 6, 2), 99.0, "MCD 01111 Spotify P4545"),
            tx(utc(2026, 7, 2), 99.0, "MCD 01222 Spotify P4546"),
            tx(utc(2026, 8, 2), 99.0, "MCD 01333 Spotify P4547"),
            tx(utc(2026, 9, 2), 119.0, "MCD 01444 Spotify P4548"),
            tx(utc(2026, 9, 14), 500.0, "Netto")
        )
        val recurring = FinanceAnalysis.recurringPayments(txs, now)
        assertEquals(1, recurring.size)
        val spotify = recurring.single()
        assertEquals(FinanceAnalysis.Cadence.MONTHLY, spotify.cadence)
        assertEquals(119.0, spotify.lastAmount, 0.0)
        assertEquals(99.0, spotify.previousAmount!!, 0.0)
        assertEquals(119.0 * 12, spotify.yearlyCost, 0.001)
    }

    @Test
    fun `a stopped subscription isn't listed`() {
        val txs = (1..4).map { tx(utc(2026, it, 5), 79.0, "Viaplay") }
        assertTrue(FinanceAnalysis.recurringPayments(txs, now).isEmpty())
    }

    @Test
    fun `monthly totals and savings rate`() {
        val txs = listOf(
            tx(utc(2026, 10, 1), 30_000.0, "Salary", TransactionType.INCOME),
            tx(utc(2026, 10, 2), 6_000.0, "Rent"),
            tx(utc(2026, 9, 5), 1_000.0, "Netto")
        )
        val months = FinanceAnalysis.monthlyTotals(txs, 2, now)
        assertEquals(1_000.0, months[0].expense, 0.0)
        assertNull(months[0].savingsRatePct)
        assertEquals(24_000.0, months[1].net, 0.0)
        assertEquals(80.0, months[1].savingsRatePct!!, 0.001)
    }

    @Test
    fun `budget pace projects to the whole month`() {
        val line = FinanceAnalysis.BudgetLine("Groceries", budget = 3_000.0, spent = 1_500.0, elapsedFraction = 0.25)
        assertEquals(6_000.0, line.projected, 0.001)
        assertTrue(line.onTrackToExceed)
    }

    @Test
    fun `annuity loan payment matches the standard formula`() {
        val loan = FinanceCalculators.loan(principal = 2_000_000.0, annualRatePct = 4.0, years = 30.0)
        assertEquals(9_548.30, loan.monthlyPayment, 0.5)
        assertEquals(360, loan.months)
        assertEquals(1_437_389.0, loan.totalInterest, 50.0)
    }

    @Test
    fun `extra payments shorten the loan and save interest`() {
        val result = FinanceCalculators.extraPayment(1_000_000.0, 4.0, 30.0, extraMonthly = 2_000.0)
        assertTrue(result.monthsSaved > 100)
        assertTrue(result.interestSaved > 200_000.0)
    }

    @Test
    fun `contribution rate counts like interest`() {
        val with = FinanceCalculators.loan(1_000_000.0, 3.0, 20.0, contributionRatePct = 1.0)
        val plain = FinanceCalculators.loan(1_000_000.0, 4.0, 20.0)
        assertEquals(plain.monthlyPayment, with.monthlyPayment, 0.001)
    }

    @Test
    fun `savings goal months and monthly amount agree`() {
        assertEquals(10, FinanceCalculators.monthsToGoal(10_000.0, 0.0, 1_000.0))
        assertEquals(0, FinanceCalculators.monthsToGoal(5_000.0, 6_000.0, 100.0))
        assertNull(FinanceCalculators.monthsToGoal(10_000.0, 0.0, 0.0))
        assertEquals(500.0, FinanceCalculators.monthlyNeeded(12_000.0, 0.0, 24), 0.001)
        val needed = FinanceCalculators.monthlyNeeded(100_000.0, 10_000.0, 60, annualReturnPct = 5.0)
        assertEquals(60, FinanceCalculators.monthsToGoal(100_000.0, 10_000.0, needed + 0.01, 5.0))
    }
}
