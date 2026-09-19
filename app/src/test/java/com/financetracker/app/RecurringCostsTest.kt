package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.anticipatedRecurringExpenseTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RecurringCostsTest {

    private var nextId = 1L

    private fun utcMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day)
        return cal.timeInMillis
    }

    private fun expense(
        date: Long,
        amount: Double,
        note: String,
        category: String = "Phone, internet, streaming and TV",
        mainCategory: String = "Media",
        accountId: Long = 1L
    ) = TransactionWithDetails(
        id = nextId++,
        amount = amount,
        type = TransactionType.EXPENSE,
        accountId = accountId,
        accountName = "Checking",
        categoryId = 1L,
        categoryName = category,
        mainCategoryName = mainCategory,
        categoryColorHex = "#000000",
        date = date,
        note = note
    )

    private fun income(date: Long, amount: Double, note: String) = TransactionWithDetails(
        id = nextId++,
        amount = amount,
        type = TransactionType.INCOME,
        accountId = 1L,
        accountName = "Checking",
        categoryId = 2L,
        categoryName = "Pay, benefits and pension",
        mainCategoryName = "Income",
        categoryColorHex = "#000000",
        date = date,
        note = note
    )

    // "Now" is March 15, 2026 — current month is March; lookback months are Feb, Jan, Dec 2025.
    private val now = utcMillis(2026, 3, 15)

    @Test
    fun `a bill in 2 of the last 3 months, not yet posted this month, is anticipated at its most recent amount`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        assertEquals(210.0, anticipatedRecurringExpenseTotal(transactions, now), 0.001)
    }

    @Test
    fun `a bill that already posted this month is not anticipated again`() {
        val transactions = listOf(
            expense(utcMillis(2025, 12, 10), 300.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 1, 10), 310.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 2, 10), 320.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 3, 10), 330.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now), 0.001)
    }

    @Test
    fun `a one-off expense in only 1 of the last 3 months does not count as recurring`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 5), 500.0, "Dentist", category = "Dentist, doctor and medication", mainCategory = "Clothing and pers. care prod.")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now), 0.001)
    }

    @Test
    fun `income is never counted, even with the same note every month`() {
        val transactions = listOf(
            income(utcMillis(2026, 1, 31), 26000.0, "Salary"),
            income(utcMillis(2026, 2, 28), 26000.0, "Salary")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now), 0.001)
    }

    @Test
    fun `two different merchant notes are tracked as separate groups, not merged into one`() {
        // Known heuristic limitation, deliberately documented rather than hidden: shopping at
        // the exact same store in each of the last 2 months, with no visit yet this month, DOES
        // qualify as "recurring" here, the same as a real bill would — this grouping has no way
        // to tell "always the same store" apart from "genuinely the same monthly commitment"
        // from note text alone. In practice groceries are visited far more than once a month, so
        // a real grocery habit almost always has a same-month visit already posted by the time
        // this runs, which excludes it. What this test actually pins down is narrower: Rema 1000
        // and Netto are never pooled into a single group just because both are "Groceries".
        val transactions = listOf(
            expense(utcMillis(2026, 1, 3), 400.0, "Rema 1000", category = "Groceries", mainCategory = "Food"),
            expense(utcMillis(2026, 1, 20), 350.0, "Netto", category = "Groceries", mainCategory = "Food"),
            expense(utcMillis(2026, 2, 4), 420.0, "Rema 1000", category = "Groceries", mainCategory = "Food"),
            expense(utcMillis(2026, 2, 18), 300.0, "Netto", category = "Groceries", mainCategory = "Food")
        )
        val total = anticipatedRecurringExpenseTotal(transactions, now)
        assertEquals(420.0 + 300.0, total, 0.001)
    }

    @Test
    fun `multiple qualifying recurring costs are summed`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia"),
            expense(
                utcMillis(2026, 1, 1),
                7000.0,
                "Transfer to savings",
                category = "Other (Transfer)",
                mainCategory = "Other"
            ),
            expense(
                utcMillis(2026, 2, 1),
                7000.0,
                "Transfer to savings",
                category = "Other (Transfer)",
                mainCategory = "Other"
            )
        )
        assertEquals(210.0 + 7000.0, anticipatedRecurringExpenseTotal(transactions, now), 0.001)
    }

    @Test
    fun `empty history anticipates nothing`() {
        assertEquals(0.0, anticipatedRecurringExpenseTotal(emptyList(), now), 0.001)
    }
}
