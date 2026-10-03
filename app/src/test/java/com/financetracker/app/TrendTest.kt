package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.CategoryFilter
import com.financetracker.app.util.TrendGranularity
import com.financetracker.app.util.buildTrend
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TrendTest {

    private fun utc(year: Int, month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day)
    }.timeInMillis

    private fun tx(
        date: Long,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = 1L,
        category: String? = "Groceries",
        main: String? = "Food"
    ) = TransactionWithDetails(
        id = 0, amount = amount, type = type, accountId = 1L, accountName = "Main",
        categoryId = categoryId, categoryName = category, mainCategoryName = main,
        categoryColorHex = null, date = date, note = ""
    )

    private val now = utc(2026, 10, 3)

    @Test
    fun `monthly trend ends with the current month and sums each month`() {
        val txs = listOf(
            tx(utc(2026, 10, 1), 100.0),
            tx(utc(2026, 9, 15), 40.0),
            tx(utc(2026, 9, 30), 60.0),
            tx(utc(2026, 9, 20), 500.0, type = TransactionType.INCOME, categoryId = 9, category = "Pay", main = "Income")
        )
        val trend = buildTrend(txs, TrendGranularity.MONTH, CategoryFilter.All, false, false, false, monthCount = 3, now = now)
        assertEquals(listOf("Aug", "Sep", "Oct"), trend.map { it.label })
        assertEquals(listOf(0.0, 100.0, 100.0), trend.map { it.expense })
        assertEquals(listOf(0.0, 500.0, 0.0), trend.map { it.income })
        assertEquals("2026", trend.first().subLabel)
        assertEquals(null, trend[1].subLabel)
    }

    @Test
    fun `the year is shown under January`() {
        val trend = buildTrend(emptyList(), TrendGranularity.MONTH, CategoryFilter.All, false, false, false, monthCount = 12, now = now)
        assertEquals("Nov", trend.first().label)
        assertEquals("2025", trend.first().subLabel)
        assertEquals("2026", trend.first { it.label == "Jan" }.subLabel)
    }

    @Test
    fun `yearly trend runs from the oldest transaction's year to this year`() {
        val txs = listOf(tx(utc(2024, 3, 1), 10.0), tx(utc(2026, 1, 5), 20.0), tx(utc(2026, 8, 5), 5.0))
        val trend = buildTrend(txs, TrendGranularity.YEAR, CategoryFilter.All, false, false, false, now = now)
        assertEquals(listOf("2024", "2025", "2026"), trend.map { it.label })
        assertEquals(listOf(10.0, 0.0, 25.0), trend.map { it.expense })
    }

    @Test
    fun `category filters narrow the trend`() {
        val txs = listOf(
            tx(utc(2026, 10, 1), 100.0),
            tx(utc(2026, 10, 2), 30.0, categoryId = 2, category = "Restaurant", main = "Food"),
            tx(utc(2026, 10, 2), 70.0, categoryId = 3, category = "Fuel", main = "Transport"),
            tx(utc(2026, 10, 2), 5.0, categoryId = null, category = null, main = null)
        )
        fun total(filter: CategoryFilter) =
            buildTrend(txs, TrendGranularity.MONTH, filter, false, false, false, monthCount = 1, now = now).single().expense
        assertEquals(205.0, total(CategoryFilter.All), 0.0)
        assertEquals(130.0, total(CategoryFilter.Main("Food")), 0.0)
        assertEquals(70.0, total(CategoryFilter.Single(3, "Fuel")), 0.0)
        assertEquals(5.0, total(CategoryFilter.Uncategorized), 0.0)
    }

    @Test
    fun `transfers are left out when excluded`() {
        val txs = listOf(
            tx(utc(2026, 10, 1), 100.0),
            tx(utc(2026, 10, 1), 1000.0, categoryId = 7, category = "Other (Transfer)", main = "Other")
        )
        val trend = buildTrend(txs, TrendGranularity.MONTH, CategoryFilter.All, false, true, false, monthCount = 1, now = now)
        assertEquals(100.0, trend.single().expense, 0.0)
    }

    @Test
    fun `category filters survive a round trip through navigation`() {
        listOf(
            CategoryFilter.All,
            CategoryFilter.Uncategorized,
            CategoryFilter.Main("Clothing and pers. care prod."),
            CategoryFilter.Single(42, "Phone, internet: streaming and TV")
        ).forEach { assertEquals(it, CategoryFilter.decode(it.encode())) }
    }
}
