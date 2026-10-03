package com.financetracker.app

import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.CategoryRestore
import com.financetracker.app.data.importexport.ParsedTransactionRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRestoreTest {

    private fun tx(id: Long, date: Long, amount: Double, note: String, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(id = id, amount = amount, type = type, accountId = 1L, categoryId = null, date = date, note = note)

    private fun row(date: Long, amount: Double, note: String, main: String, category: String, type: TransactionType = TransactionType.EXPENSE) =
        ParsedTransactionRow(rowNumber = 0, date = date, note = note, mainCategoryName = main, categoryName = category, type = type, amount = amount)

    @Test
    fun `a backup row matches the same transaction by date, amount, type and note`() {
        val existing = listOf(tx(1, 100L, 199.0, "MCD 01990 SpotifySE"))
        val plan = CategoryRestore.plan(existing, listOf(row(100L, 199.0, "mcd 01990 spotifyse ", "Media", "Phone, internet, streaming and TV")))
        assertEquals(1, plan.matches.size)
        assertEquals(1L, plan.matches.first().transaction.id)
        assertEquals(0, plan.unmatchedCount)
    }

    @Test
    fun `identical same-day transactions are paired one-to-one`() {
        val existing = listOf(tx(1, 100L, 45.0, "Coffee"), tx(2, 100L, 45.0, "Coffee"))
        val backup = listOf(
            row(100L, 45.0, "Coffee", "Leisure", "Café, restaurant and bar"),
            row(100L, 45.0, "Coffee", "Leisure", "Café, restaurant and bar"),
            row(100L, 45.0, "Coffee", "Leisure", "Café, restaurant and bar")
        )
        val plan = CategoryRestore.plan(existing, backup)
        assertEquals(listOf(1L, 2L), plan.matches.map { it.transaction.id })
        assertEquals(1, plan.unmatchedCount)
    }

    @Test
    fun `uncategorized backup rows are ignored rather than wiping a category`() {
        val existing = listOf(tx(1, 100L, 50.0, "Netto"))
        val plan = CategoryRestore.plan(existing, listOf(row(100L, 50.0, "Netto", "Uncategorized", "Uncategorized")))
        assertEquals(0, plan.matches.size)
        assertEquals(0, plan.unmatchedCount)
    }

    @Test
    fun `a different amount, date or direction doesn't match`() {
        val existing = listOf(tx(1, 100L, 50.0, "Netto"))
        val backup = listOf(
            row(100L, 51.0, "Netto", "Food", "Groceries"),
            row(200L, 50.0, "Netto", "Food", "Groceries"),
            row(100L, 50.0, "Netto", "Income", "Other income", TransactionType.INCOME)
        )
        val plan = CategoryRestore.plan(existing, backup)
        assertEquals(0, plan.matches.size)
        assertEquals(3, plan.unmatchedCount)
    }
}
