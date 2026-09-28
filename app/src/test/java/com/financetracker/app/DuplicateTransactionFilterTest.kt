package com.financetracker.app

import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.DuplicateTransactionFilter
import com.financetracker.app.data.importexport.ParsedTransactionRow
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateTransactionFilterTest {

    private fun row(date: Long, amount: Double, type: TransactionType, note: String) = ParsedTransactionRow(
        rowNumber = 1,
        date = date,
        note = note,
        mainCategoryName = "Main",
        categoryName = "Category",
        type = type,
        amount = amount
    )

    private fun transaction(date: Long, amount: Double, type: TransactionType, note: String) = Transaction(
        amount = amount,
        type = type,
        accountId = 1L,
        categoryId = 1L,
        date = date,
        note = note
    )

    @Test
    fun `re-importing the same file skips every row as a duplicate`() {
        val existing = listOf(
            transaction(1000L, 54.30, TransactionType.EXPENSE, "Grocery Store"),
            transaction(2000L, 2000.0, TransactionType.INCOME, "Paycheck")
        )
        val rows = listOf(
            row(1000L, 54.30, TransactionType.EXPENSE, "Grocery Store"),
            row(2000L, 2000.0, TransactionType.INCOME, "Paycheck")
        )

        val result = DuplicateTransactionFilter.filter(existing, rows)

        assertEquals(0, result.uniqueRows.size)
        assertEquals(2, result.duplicateCount)
    }

    @Test
    fun `only new rows in an overlapping file are kept`() {
        val existing = listOf(
            transaction(1000L, 54.30, TransactionType.EXPENSE, "Grocery Store")
        )
        val rows = listOf(
            row(1000L, 54.30, TransactionType.EXPENSE, "Grocery Store"), // duplicate
            row(3000L, 12.00, TransactionType.EXPENSE, "Coffee Shop") // new
        )

        val result = DuplicateTransactionFilter.filter(existing, rows)

        assertEquals(1, result.uniqueRows.size)
        assertEquals("Coffee Shop", result.uniqueRows[0].note)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `genuinely repeated same-day transactions are matched by count, not just presence`() {
        // Two identical coffees already exist.
        val existing = listOf(
            transaction(1000L, 4.50, TransactionType.EXPENSE, "Coffee"),
            transaction(1000L, 4.50, TransactionType.EXPENSE, "Coffee")
        )
        // The file has three identical coffee rows: two are the ones already imported, one is new.
        val rows = listOf(
            row(1000L, 4.50, TransactionType.EXPENSE, "Coffee"),
            row(1000L, 4.50, TransactionType.EXPENSE, "Coffee"),
            row(1000L, 4.50, TransactionType.EXPENSE, "Coffee")
        )

        val result = DuplicateTransactionFilter.filter(existing, rows)

        assertEquals(1, result.uniqueRows.size)
        assertEquals(2, result.duplicateCount)
    }

    @Test
    fun `note casing and whitespace differences still match as duplicates`() {
        val existing = listOf(
            transaction(1000L, 20.0, TransactionType.EXPENSE, "Netflix Subscription")
        )
        val rows = listOf(
            row(1000L, 20.0, TransactionType.EXPENSE, "  netflix subscription  ")
        )

        val result = DuplicateTransactionFilter.filter(existing, rows)

        assertEquals(0, result.uniqueRows.size)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `a different amount on the same day is not treated as a duplicate`() {
        val existing = listOf(
            transaction(1000L, 54.30, TransactionType.EXPENSE, "Grocery Store")
        )
        val rows = listOf(
            row(1000L, 60.00, TransactionType.EXPENSE, "Grocery Store")
        )

        val result = DuplicateTransactionFilter.filter(existing, rows)

        assertEquals(1, result.uniqueRows.size)
        assertEquals(0, result.duplicateCount)
    }
}
