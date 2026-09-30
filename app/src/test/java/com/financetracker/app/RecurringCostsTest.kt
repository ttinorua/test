package com.financetracker.app

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.util.anticipatedRecurringExpenseTotal
import com.financetracker.app.util.anticipatedRecurringExpenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        accountId: Long = 1L,
        categoryId: Long = 1L
    ) = TransactionWithDetails(
        id = nextId++,
        amount = amount,
        type = TransactionType.EXPENSE,
        accountId = accountId,
        accountName = "Checking",
        categoryId = categoryId,
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

    // The default category (id 1, "Phone, internet, streaming and TV") is marked Fixe.
    private val fixed = setOf(1L)

    @Test
    fun `a Fixe bill not yet posted this month is anticipated at its most recent amount`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        assertEquals(210.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `a bill that already posted this month is not anticipated again`() {
        val transactions = listOf(
            expense(utcMillis(2025, 12, 10), 300.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 1, 10), 310.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 2, 10), 320.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home"),
            expense(utcMillis(2026, 3, 10), 330.0, "Elgiganten electricity", category = "Electricity", mainCategory = "Home")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `a category not marked Fixe is never anticipated, even when it repeats every month`() {
        val transactions = listOf(
            expense(utcMillis(2025, 12, 18), 340.0, "MCD 01886 SILVAN A/S", category = "Furniture and home accessories", mainCategory = "Home", categoryId = 6L),
            expense(utcMillis(2026, 1, 20), 120.0, "MCD 01930 SILVAN A/S", category = "Furniture and home accessories", mainCategory = "Home", categoryId = 6L),
            expense(utcMillis(2026, 2, 23), 340.75, "MCD 01986 SILVAN A/S", category = "Furniture and home accessories", mainCategory = "Home", categoryId = 6L)
        )
        assertTrue(anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed).isEmpty())
    }

    @Test
    fun `income is never counted, even with the same note every month`() {
        val transactions = listOf(
            income(utcMillis(2026, 1, 31), 26000.0, "Salary"),
            income(utcMillis(2026, 2, 28), 26000.0, "Salary")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `two different merchant notes are tracked as separate groups, not merged into one`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 3), 400.0, "Café Norden", category = "Café, restaurant and bar", mainCategory = "Leisure"),
            expense(utcMillis(2026, 1, 20), 350.0, "Sunset Bar", category = "Café, restaurant and bar", mainCategory = "Leisure"),
            expense(utcMillis(2026, 2, 4), 420.0, "Café Norden", category = "Café, restaurant and bar", mainCategory = "Leisure"),
            expense(utcMillis(2026, 2, 18), 300.0, "Sunset Bar", category = "Café, restaurant and bar", mainCategory = "Leisure")
        )
        val total = anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed)
        assertEquals(420.0 + 300.0, total, 0.001)
    }

    @Test
    fun `groceries is never anticipated, even when it repeats like a real bill would`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 3), 400.0, "Rema 1000", category = "Groceries", mainCategory = "Food"),
            expense(utcMillis(2026, 2, 4), 420.0, "Rema 1000", category = "Groceries", mainCategory = "Food")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `groceries is excluded even if the category is marked Fixe`() {
        val transactions = listOf(
            expense(utcMillis(2026, 2, 3), 400.0, "Rema 1000", category = "Groceries", mainCategory = "Food", categoryId = 9L)
        )
        val total = anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = setOf(9L))
        assertEquals(0.0, total, 0.001)
    }

    @Test
    fun `fuel is never anticipated, even when it repeats like a real bill would`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 12), 350.0, "Spiiri", category = "Fuel", mainCategory = "Transportation"),
            expense(utcMillis(2026, 2, 13), 360.0, "Spiiri", category = "Fuel", mainCategory = "Transportation")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `fuel is excluded even if the category is marked Fixe`() {
        val transactions = listOf(
            expense(utcMillis(2026, 2, 12), 350.0, "Spiiri", category = "Fuel", mainCategory = "Transportation", categoryId = 10L)
        )
        val total = anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = setOf(10L))
        assertEquals(0.0, total, 0.001)
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
        assertEquals(210.0 + 7000.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `empty history anticipates nothing`() {
        assertEquals(0.0, anticipatedRecurringExpenseTotal(emptyList(), now), 0.001)
    }

    @Test
    fun `a consistent day of month predicts that day this month, not flagged as estimated`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Telia", item.label)
        assertEquals(210.0, item.amount, 0.001)
        assertFalse(item.dateIsEstimated)
        assertEquals(utcMillis(2026, 3, 15), item.estimatedDate)
    }

    @Test
    fun `an irregular gap is flagged as estimated and assumes monthly from the last posting`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 3), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 27), 210.0, "Telia")
        )
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed)
        assertEquals(1, items.size)
        val item = items.first()
        assertTrue(item.dateIsEstimated)
        assertEquals(utcMillis(2026, 3, 27), item.estimatedDate)
    }

    @Test
    fun `multiple anticipated items are sorted by predicted date`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 27), 210.0, "Telia"),
            expense(utcMillis(2026, 2, 27), 210.0, "Telia"),
            expense(
                utcMillis(2026, 1, 3),
                7000.0,
                "Transfer to savings",
                category = "Other (Transfer)",
                mainCategory = "Other"
            ),
            expense(
                utcMillis(2026, 2, 3),
                7000.0,
                "Transfer to savings",
                category = "Other (Transfer)",
                mainCategory = "Other"
            )
        )
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed)
        assertEquals(listOf("Transfer to savings", "Telia"), items.map { it.label })
    }

    @Test
    fun `a category marked Fixe is anticipated off a single occurrence within the last 2 months`() {
        val transactions = listOf(
            expense(
                utcMillis(2026, 2, 20),
                450.0,
                "Tryg forsikring",
                category = "Union and unemployment insurance",
                mainCategory = "Insurance",
                categoryId = 5L
            )
        )
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = setOf(5L))
        assertEquals(1, items.size)
        assertEquals(450.0, items.first().amount, 0.001)
    }

    @Test
    fun `a Fixe category whose only occurrence is more than a month old is not anticipated for this month`() {
        val transactions = listOf(
            expense(
                utcMillis(2025, 12, 20),
                450.0,
                "Tryg forsikring",
                category = "Union and unemployment insurance",
                mainCategory = "Insurance",
                categoryId = 5L
            )
        )
        val total = anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = setOf(5L))
        assertEquals(0.0, total, 0.001)
    }

    @Test
    fun `several categories marked Fixe are all anticipated off a single recent occurrence`() {
        val transactions = listOf(
            expense(utcMillis(2026, 2, 5), 1200.0, "Santander loan", category = "Consumer loan", mainCategory = "Loan and debt", categoryId = 10L),
            expense(utcMillis(2026, 2, 5), 50.0, "Card interest", category = "Interest and fees", mainCategory = "Loan and debt", categoryId = 11L),
            expense(utcMillis(2026, 2, 5), 300.0, "Misc debt", category = "Loan and debt (Other)", mainCategory = "Loan and debt", categoryId = 12L),
            expense(utcMillis(2026, 2, 5), 600.0, "Norlys", category = "Electricity", mainCategory = "Home", categoryId = 13L)
        )
        val fixedIds = setOf(10L, 11L, 12L, 13L)
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixedIds)
        assertEquals(4, items.size)
        assertEquals(setOf(1200.0, 50.0, 300.0, 600.0), items.map { it.amount }.toSet())
    }

    @Test
    fun `a single occurrence in a category not marked Fixe is not anticipated`() {
        val transactions = listOf(
            expense(utcMillis(2026, 2, 5), 800.0, "Card payment", category = "Credit cards", mainCategory = "Loan and debt", categoryId = 14L)
        )
        // fixedCategoryIds deliberately left empty/not containing 14L
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `a dismissed key is excluded and does not count toward the total`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        val dismissKey = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed).first().dismissKey
        val afterDismiss = anticipatedRecurringExpenses(transactions, now, dismissedKeys = setOf(dismissKey), fixedCategoryIds = fixed)
        assertTrue(afterDismiss.isEmpty())
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, dismissedKeys = setOf(dismissKey), fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `parking is never anticipated, even when it repeats like a real bill would`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 10), 50.0, "City Parkering", category = "Parking", mainCategory = "Transportation"),
            expense(utcMillis(2026, 2, 10), 50.0, "City Parkering", category = "Parking", mainCategory = "Transportation")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `a detected yearly cadence on a Fixe category is anticipated only in the month it predicts next`() {
        val transactions = listOf(
            expense(
                utcMillis(2024, 3, 3), 5000.0, "Tryg forsikring",
                category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 5L
            ),
            expense(
                utcMillis(2025, 3, 5), 5200.0, "Tryg forsikring",
                category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 5L
            )
        )
        val fixedIds = setOf(5L)
        val thisMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 0, fixedCategoryIds = fixedIds)
        assertEquals(1, thisMonth.size)
        assertEquals(5200.0, thisMonth.first().amount, 0.001)
        assertFalse(thisMonth.first().dateIsEstimated)
        assertEquals(utcMillis(2026, 3, 5), thisMonth.first().estimatedDate)

        val nextMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 1, fixedCategoryIds = fixedIds)
        assertTrue(nextMonth.isEmpty())
    }

    @Test
    fun `a detected quarterly cadence on a Fixe category predicts 3 months after the last occurrence`() {
        val transactions = listOf(
            expense(
                utcMillis(2025, 9, 5), 300.0, "Alka forsikring",
                category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 5L
            ),
            expense(
                utcMillis(2025, 12, 5), 310.0, "Alka forsikring",
                category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 5L
            )
        )
        val items = anticipatedRecurringExpenses(transactions, now, monthsAhead = 0, fixedCategoryIds = setOf(5L))
        assertEquals(1, items.size)
        assertFalse(items.first().dateIsEstimated)
        assertEquals(utcMillis(2026, 3, 5), items.first().estimatedDate)
    }

    @Test
    fun `anticipating next month includes a monthly bill that hasn't posted this month either`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        val nextMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 1, fixedCategoryIds = fixed)
        assertEquals(1, nextMonth.size)
        assertEquals(utcMillis(2026, 4, 15), nextMonth.first().estimatedDate)
    }

    @Test
    fun `a running reference number embedded in the note doesn't split one bill into separate groups`() {
        // Sydbank threads a transaction reference straight into the note (e.g. "MCD 01978
        // Telenor"), incrementing on every posting even for the exact same recurring bill.
        val transactions = listOf(
            expense(utcMillis(2026, 1, 26), 200.0, "MCD 01900 Telenor"),
            expense(utcMillis(2026, 2, 26), 210.0, "MCD 01978 Telenor")
        )
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed)
        assertEquals(1, items.size)
        assertEquals(210.0, items.first().amount, 0.001)
    }

    @Test
    fun `a real posting with a different embedded reference number still counts as already posted`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 26), 200.0, "MCD 01900 Telenor"),
            expense(utcMillis(2026, 2, 26), 210.0, "MCD 01978 Telenor"),
            // This month's real posting — same bill, but yet another reference number. Without
            // stripping it, this would be treated as a brand-new, unrelated group, and the
            // "already posted" check on the older group would never see it, leaving a stale
            // "Upcoming" entry showing for a bill that already posted.
            expense(utcMillis(2026, 3, 5), 220.0, "MCD 02027 Telenor")
        )
        assertEquals(0.0, anticipatedRecurringExpenseTotal(transactions, now, fixedCategoryIds = fixed), 0.001)
    }

    @Test
    fun `a bill already posted next month is not anticipated again for next month`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia"),
            expense(utcMillis(2026, 4, 1), 220.0, "Telia")
        )
        val nextMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 1, fixedCategoryIds = fixed)
        assertTrue(nextMonth.isEmpty())
    }

    @Test
    fun `a posting recategorized this month still counts as the bill having posted`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 6), 69.0, "MCD 01901 Prime Video", category = "Films, music, apps and software", categoryId = 3L),
            expense(utcMillis(2026, 2, 6), 69.0, "MCD 01947 Prime Video", category = "Films, music, apps and software", categoryId = 3L),
            expense(utcMillis(2026, 3, 7), 69.0, "MCD 01993 Prime Video", categoryId = 1L)
        )
        assertTrue(anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = setOf(1L)).isEmpty())
    }

    @Test
    fun `a recategorized series is anticipated next month under its latest category`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 6), 69.0, "MCD 01901 Prime Video", category = "Films, music, apps and software", categoryId = 3L),
            expense(utcMillis(2026, 2, 6), 69.0, "MCD 01947 Prime Video", category = "Films, music, apps and software", categoryId = 3L),
            expense(utcMillis(2026, 3, 7), 69.0, "MCD 01993 Prime Video", categoryId = 1L)
        )
        val nextMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 1, fixedCategoryIds = setOf(1L))
        assertEquals(1, nextMonth.size)
        assertEquals("Phone, internet, streaming and TV", nextMonth.first().category)
    }

    @Test
    fun `a payment code or regional suffix in the note doesn't split one subscription`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 6), 199.0, "MCD 01899 SpotifySE"),
            expense(utcMillis(2026, 2, 4), 199.0, "MCD 01943 Spotify P45451234AB"),
            expense(utcMillis(2026, 3, 4), 199.0, "MCD 01990 SpotifySE")
        )
        assertTrue(anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = setOf(1L)).isEmpty())
    }

    @Test
    fun `different merchants sharing a short prefix are not merged`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 10), 100.0, "MobilePay Jens"),
            expense(utcMillis(2026, 2, 10), 100.0, "MobilePay Jens"),
            expense(utcMillis(2026, 3, 10), 50.0, "MobilePay Anne")
        )
        assertEquals(1, anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed).size)
    }

    @Test
    fun `next month includes a Fixe bill even when it hasn't posted this month yet`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 20), 189.0, "MCD 01900 Netflix.com"),
            expense(utcMillis(2026, 2, 23), 189.0, "MCD 01950 Netflix.com")
        )
        val nextMonth = anticipatedRecurringExpenses(transactions, now, monthsAhead = 1, fixedCategoryIds = fixed)
        assertEquals(1, nextMonth.size)
        assertEquals(utcMillis(2026, 4, 23), nextMonth.first().estimatedDate)
    }

    @Test
    fun `a series moved out of a Fixe category stops being anticipated`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 10), 699.0, "MobilePay Telenor"),
            expense(utcMillis(2026, 2, 10), 699.0, "MobilePay Telenor", category = "Other (Transfer)", mainCategory = "Other", categoryId = 7L)
        )
        assertTrue(anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed).isEmpty())
    }

    @Test
    fun `a bank-scheduled payment is listed at its exact date and amount, even with no history or Fixe category`() {
        val scheduled = listOf(
            expense(utcMillis(2026, 4, 1), 3055.0, "Tryg forsikring", category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 20L)
        )
        val nextMonth = anticipatedRecurringExpenses(emptyList(), now, monthsAhead = 1, scheduled = scheduled)
        assertEquals(1, nextMonth.size)
        val item = nextMonth.first()
        assertTrue(item.scheduledByBank)
        assertFalse(item.dateIsEstimated)
        assertEquals(3055.0, item.amount, 0.001)
        assertEquals(utcMillis(2026, 4, 1), item.estimatedDate)
    }

    @Test
    fun `a bank-scheduled payment in another month isn't listed for this month`() {
        val scheduled = listOf(expense(utcMillis(2026, 4, 1), 3055.0, "Tryg forsikring"))
        assertTrue(anticipatedRecurringExpenses(emptyList(), now, scheduled = scheduled).isEmpty())
    }

    @Test
    fun `a bank-scheduled payment replaces the Fixe prediction for the same bill`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        val scheduled = listOf(expense(utcMillis(2026, 3, 20), 250.0, "Telia"))
        val items = anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed, scheduled = scheduled)
        assertEquals(1, items.size)
        assertTrue(items.first().scheduledByBank)
        assertEquals(250.0, items.first().amount, 0.001)
        assertEquals(utcMillis(2026, 3, 20), items.first().estimatedDate)
    }

    @Test
    fun `a bill the bank has scheduled for next month isn't also predicted for this month`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 15), 200.0, "Telia"),
            expense(utcMillis(2026, 2, 15), 210.0, "Telia")
        )
        val scheduled = listOf(expense(utcMillis(2026, 4, 1), 210.0, "Telia"))
        assertTrue(anticipatedRecurringExpenses(transactions, now, fixedCategoryIds = fixed, scheduled = scheduled).isEmpty())
    }

    @Test
    fun `a bank-scheduled payment that has already posted is not listed again`() {
        val transactions = listOf(expense(utcMillis(2026, 3, 14), 700.0, "Telenor"))
        val scheduled = listOf(expense(utcMillis(2026, 3, 15), 700.0, "Telenor"))
        assertTrue(anticipatedRecurringExpenses(transactions, now, scheduled = scheduled).isEmpty())
    }

    @Test
    fun `a bank-scheduled payment borrows the category of the matching bill in history`() {
        val transactions = listOf(
            expense(utcMillis(2026, 1, 1), 3055.0, "Tryg forsikring", category = "Union and unemployment insurance", mainCategory = "Insurance", categoryId = 5L)
        )
        val scheduled = listOf(
            expense(utcMillis(2026, 3, 25), 3055.0, "Tryg forsikring").copy(categoryId = null, categoryName = null, mainCategoryName = null)
        )
        val items = anticipatedRecurringExpenses(transactions, now, scheduled = scheduled)
        assertEquals(1, items.size)
        assertEquals("Union and unemployment insurance", items.first().category)
    }

    @Test
    fun `a bank-scheduled transfer is left out when transfers are excluded`() {
        val scheduled = listOf(
            expense(utcMillis(2026, 3, 28), 5000.0, "Til opsparing", category = "Other (Transfer)", mainCategory = "Other", categoryId = 7L)
        )
        assertTrue(anticipatedRecurringExpenses(emptyList(), now, scheduled = scheduled, excludeTransfers = true).isEmpty())
        assertEquals(1, anticipatedRecurringExpenses(emptyList(), now, scheduled = scheduled, excludeTransfers = false).size)
    }

    @Test
    fun `two identical bank-scheduled payments get distinct dismiss keys`() {
        val scheduled = listOf(
            expense(utcMillis(2026, 3, 28), 100.0, "MobilePay Jens"),
            expense(utcMillis(2026, 3, 28), 100.0, "MobilePay Jens")
        )
        val items = anticipatedRecurringExpenses(emptyList(), now, scheduled = scheduled)
        assertEquals(2, items.size)
        assertEquals(2, items.map { it.dismissKey }.toSet().size)
    }
}
