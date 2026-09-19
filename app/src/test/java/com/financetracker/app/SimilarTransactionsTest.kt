package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.findSimilarTransactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarTransactionsTest {

    private var nextId = 1L

    private fun tx(
        amount: Double,
        note: String,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = 1L,
        categoryName: String? = "Groceries"
    ) = TransactionWithDetails(
        id = nextId++,
        amount = amount,
        type = type,
        accountId = 1L,
        accountName = "Checking",
        categoryId = categoryId,
        categoryName = categoryName,
        mainCategoryName = "Home",
        categoryColorHex = "#000000",
        date = 0L,
        note = note
    )

    @Test
    fun `transaction with identical note is similar`() {
        val edited = tx(50.0, "Netflix")
        val other = tx(60.0, "Netflix")
        val unrelated = tx(70.0, "Spotify")

        val result = findSimilarTransactions(listOf(edited, other, unrelated), edited, newCategoryId = 2L)

        assertEquals(listOf(other.id), result.map { it.id })
    }

    @Test
    fun `note match is case-insensitive and trims whitespace`() {
        val edited = tx(50.0, "Netflix")
        val other = tx(60.0, "  NETFLIX  ")

        val result = findSimilarTransactions(listOf(edited, other), edited, newCategoryId = 2L)

        assertEquals(listOf(other.id), result.map { it.id })
    }

    @Test
    fun `transaction with identical amount is similar even with a different note`() {
        val edited = tx(123.45, "Rema 1000")
        val other = tx(123.45, "Netto")
        val unrelated = tx(99.0, "Bilka")

        val result = findSimilarTransactions(listOf(edited, other, unrelated), edited, newCategoryId = 2L)

        assertEquals(listOf(other.id), result.map { it.id })
    }

    @Test
    fun `the edited transaction itself is never included`() {
        val edited = tx(50.0, "Netflix")

        val result = findSimilarTransactions(listOf(edited), edited, newCategoryId = 2L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `transactions already filed under the new category are excluded`() {
        val edited = tx(50.0, "Netflix", categoryId = 1L)
        val alreadyThere = tx(60.0, "Netflix", categoryId = 2L)

        val result = findSimilarTransactions(listOf(edited, alreadyThere), edited, newCategoryId = 2L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `different transaction type never matches even with the same note`() {
        val edited = tx(50.0, "Refund", type = TransactionType.EXPENSE)
        val income = tx(50.0, "Refund", type = TransactionType.INCOME)

        val result = findSimilarTransactions(listOf(edited, income), edited, newCategoryId = 2L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank note never matches other blank notes on note alone, only on amount`() {
        val edited = tx(50.0, "")
        val otherBlank = tx(60.0, "")
        val sameAmount = tx(50.0, "")

        val result = findSimilarTransactions(listOf(edited, otherBlank, sameAmount), edited, newCategoryId = 2L)

        assertEquals(listOf(sameAmount.id), result.map { it.id })
    }

    @Test
    fun `searches across the whole list, not just a filtered subset`() {
        val edited = tx(50.0, "Netflix")
        val monthsAgo = tx(50.0, "Netflix")

        val result = findSimilarTransactions(listOf(edited, monthsAgo), edited, newCategoryId = 2L)

        assertEquals(listOf(monthsAgo.id), result.map { it.id })
    }
}
