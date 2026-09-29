package com.financetracker.app

import com.financetracker.app.util.countsTowardTotals
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
        assertTrue(countsTowardTotals("Other", "Other (Transfer)", enabled = false))
    }

    @Test
    fun `enabled setting excludes a transfer in either direction`() {
        assertFalse(countsTowardTotals("Other", "Other (Transfer)", enabled = true))
    }

    @Test
    fun `enabled setting still counts a non-transfer`() {
        assertTrue(countsTowardTotals("Food", "Groceries", enabled = true))
    }

    @Test
    fun `enabled setting still counts real income`() {
        assertTrue(countsTowardTotals("Income", "Pay, benefits and pension", enabled = true))
    }

    @Test
    fun `enabled setting with null category counts`() {
        assertEquals(true, countsTowardTotals(null, null, enabled = true))
    }
}
