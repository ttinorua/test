package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.isTransferCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFilterTest {

    @Test
    fun `matches the real taxonomy's Other (Transfer) category`() {
        assertTrue(isTransferCategory("Other", "Other (Transfer)"))
    }

    @Test
    fun `is case-insensitive`() {
        assertTrue(isTransferCategory("OTHER", "other (transfer)"))
    }

    @Test
    fun `does not match a different Other category`() {
        assertFalse(isTransferCategory("Other", "Other (Cash)"))
        assertFalse(isTransferCategory("Other", "Other expense"))
    }

    @Test
    fun `null category never matches`() {
        assertFalse(isTransferCategory(null, null))
    }

    @Test
    fun `disabled setting always counts, even a transfer`() {
        assertTrue(countsTowardSpending(TransactionType.EXPENSE, "Other", "Other (Transfer)", enabled = false))
    }

    @Test
    fun `enabled setting excludes a transfer expense`() {
        assertFalse(countsTowardSpending(TransactionType.EXPENSE, "Other", "Other (Transfer)", enabled = true))
    }

    @Test
    fun `enabled setting still counts a non-transfer expense`() {
        assertTrue(countsTowardSpending(TransactionType.EXPENSE, "Food", "Groceries", enabled = true))
    }

    @Test
    fun `enabled setting never excludes income, even with the same category strings`() {
        assertTrue(countsTowardSpending(TransactionType.INCOME, "Other", "Other (Transfer)", enabled = true))
    }

    @Test
    fun `enabled setting with null category counts`() {
        assertEquals(true, countsTowardSpending(TransactionType.EXPENSE, null, null, enabled = true))
    }
}
