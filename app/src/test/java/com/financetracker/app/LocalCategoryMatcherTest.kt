package com.financetracker.app

import com.financetracker.app.data.ai.LocalCategoryMatcher
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCategoryMatcherTest {

    private val categories = listOf(
        Category(id = 1, name = "Groceries", mainCategory = "Food", type = TransactionType.EXPENSE),
        Category(id = 2, name = "Fuel", mainCategory = "Transport", type = TransactionType.EXPENSE),
        Category(id = 3, name = "Salary", mainCategory = "Income", type = TransactionType.INCOME)
    )

    @Test
    fun `matches a known grocery chain`() {
        val result = LocalCategoryMatcher.suggest("NETTO BUTIK 1234 AARHUS", categories)
        assertEquals("Groceries", result?.name)
    }

    @Test
    fun `matches a known fuel station`() {
        val result = LocalCategoryMatcher.suggest("SHELL SERVICE STATION", categories)
        assertEquals("Fuel", result?.name)
    }

    @Test
    fun `matches salary as income not expense`() {
        val result = LocalCategoryMatcher.suggest("LOEN SEPTEMBER", categories)
        assertEquals("Salary", result?.name)
        assertEquals(TransactionType.INCOME, result?.type)
    }

    @Test
    fun `returns null when pattern matches but user has no corresponding category`() {
        val noStreamingCategory = categories // none of these are Entertainment/Streaming
        val result = LocalCategoryMatcher.suggest("NETFLIX.COM", noStreamingCategory)
        assertNull(result)
    }

    @Test
    fun `returns null for an unrecognized merchant`() {
        val result = LocalCategoryMatcher.suggest("SOME RANDOM LOCAL SHOP XYZ", categories)
        assertNull(result)
    }

    @Test
    fun `never matches an income pattern against an expense category of the same name`() {
        val salaryAsExpense = listOf(
            Category(id = 9, name = "Salary", mainCategory = "Income", type = TransactionType.EXPENSE)
        )
        val result = LocalCategoryMatcher.suggest("LOEN SEPTEMBER", salaryAsExpense)
        assertNull(result)
    }
}
