package com.financetracker.app

import com.financetracker.app.data.bank.LegacyCategories
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCategoriesTest {

    private fun category(mainCategory: String, name: String, type: TransactionType) =
        Category(name = name, mainCategory = mainCategory, type = type)

    @Test
    fun `old generic Transportation bucket is ambiguous`() {
        assertTrue(
            LegacyCategories.isAmbiguousBucket(
                category("Transportation", "Transportation", TransactionType.EXPENSE)
            )
        )
    }

    @Test
    fun `old generic Utilities, Dining and Entertainment buckets are ambiguous`() {
        assertTrue(LegacyCategories.isAmbiguousBucket(category("Home", "Utilities", TransactionType.EXPENSE)))
        assertTrue(LegacyCategories.isAmbiguousBucket(category("Leisure", "Dining", TransactionType.EXPENSE)))
        assertTrue(LegacyCategories.isAmbiguousBucket(category("Leisure", "Entertainment", TransactionType.EXPENSE)))
    }

    @Test
    fun `a real taxonomy category with the same name but different main category is not ambiguous`() {
        // "Transportation (Other)" lives under mainCategory "Transportation" too, but has a
        // different name, so it must never be swept up as if it were the old generic bucket.
        assertFalse(
            LegacyCategories.isAmbiguousBucket(
                category("Transportation", "Transportation (Other)", TransactionType.EXPENSE)
            )
        )
    }

    @Test
    fun `an unrelated real taxonomy category is not ambiguous`() {
        assertFalse(LegacyCategories.isAmbiguousBucket(category("Home", "Electricity", TransactionType.EXPENSE)))
        assertFalse(
            LegacyCategories.isAmbiguousBucket(category("Income", "Pay, benefits and pension", TransactionType.INCOME))
        )
    }

    @Test
    fun `matching name and main category but wrong type is not ambiguous`() {
        assertFalse(LegacyCategories.isAmbiguousBucket(category("Home", "Utilities", TransactionType.INCOME)))
    }
}
