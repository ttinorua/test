package com.financetracker.app

import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.Loan
import com.financetracker.app.data.prefs.SavingsGoal
import com.financetracker.app.util.advisor.AttentionChecks
import com.financetracker.app.util.advisor.AttentionInput
import com.financetracker.app.util.advisor.AttentionLevel
import com.financetracker.app.util.advisor.BankConsent
import com.financetracker.app.util.advisor.GoalTracking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AttentionChecksTest {

    private fun utc(year: Int, month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day)
    }.timeInMillis

    private val groceries = Category(id = 1, name = "Groceries", mainCategory = "Food", type = TransactionType.EXPENSE)
    private val rent = Category(id = 2, name = "Rent", mainCategory = "Housing", type = TransactionType.EXPENSE)
    private var nextId = 1L

    private fun tx(date: Long, amount: Double, note: String, category: Category? = groceries, type: TransactionType = TransactionType.EXPENSE) =
        TransactionWithDetails(
            id = nextId++, amount = amount, type = type, accountId = 1, accountName = "Main",
            categoryId = category?.id, categoryName = category?.name, mainCategoryName = category?.mainCategory,
            categoryColorHex = null, date = date, note = note
        )

    private fun input(
        now: Long,
        transactions: List<TransactionWithDetails> = emptyList(),
        balances: Map<Long, Double> = mapOf(1L to 5000.0, 2L to 20000.0),
        categoryBudgets: Map<Pair<Long, Long?>, Double> = emptyMap(),
        goals: List<SavingsGoal> = emptyList(),
        loans: List<Loan> = emptyList(),
        consents: List<BankConsent> = emptyList()
    ) = AttentionInput(
        now = now, currency = "DKK", transactions = transactions,
        accounts = listOf(Account(id = 1, name = "Main"), Account(id = 2, name = "Savings")),
        balances = balances, categories = listOf(groceries, rent), uncategorizedCategoryIds = emptySet(),
        excludeTransfers = true, shiftSalary = false, mainAccountId = 1,
        overallBudgets = emptyMap(), categoryBudgets = categoryBudgets, fixedCategoryIds = setOf(rent.id),
        goals = goals, loans = loans, consents = consents, lastSyncFailure = null,
        backupEnabled = false, backupLastSuccessAt = null, backupLastError = null
    )

    @Test
    fun `nothing to report gives an empty list`() {
        assertTrue(AttentionChecks.run(input(utc(2026, 10, 15))).isEmpty())
    }

    @Test
    fun `a budget on pace to be exceeded is flagged with its transactions`() {
        val now = utc(2026, 10, 15)
        val items = AttentionChecks.run(
            input(now, transactions = listOf(tx(utc(2026, 10, 3), 1500.0, "Netto"), tx(utc(2026, 10, 12), 1400.0, "Føtex")), categoryBudgets = mapOf((1L to null) to 4000.0))
        )
        val item = items.single { it.key.startsWith("budget-pace:") }
        assertEquals(AttentionLevel.WARNING, item.level)
        assertEquals(1L, item.filter?.categoryId)
        assertTrue(item.notify)
    }

    @Test
    fun `an exceeded budget says by how much`() {
        val items = AttentionChecks.run(
            input(utc(2026, 10, 20), transactions = listOf(tx(utc(2026, 10, 3), 4500.0, "Netto")), categoryBudgets = mapOf((1L to null) to 4000.0))
        )
        val item = items.single { it.key.startsWith("budget-over:") }
        assertTrue(item.detail, item.detail.contains("500.00"))
    }

    @Test
    fun `a goal falling behind says when it will be reached instead`() {
        val now = utc(2026, 10, 3)
        val goal = SavingsGoal(
            id = "car", name = "Car fund", targetAmount = 60_000.0, accountId = 2, targetDate = "2027-06-30",
            startedAt = utc(2026, 4, 3), startAmount = 14_000.0
        )
        // 6,000 saved in 6 months (1,000/month), with 40,000 still to go in 9 months.
        val item = AttentionChecks.run(input(now, goals = listOf(goal))).single { it.key.startsWith("goal-behind:car") }
        assertTrue(item.detail, item.detail.contains("instead of June 2027"))
        assertTrue(item.detail, item.detail.contains("a month would make it"))
        assertEquals("goal-behind:car:2026-10", item.key)
    }

    @Test
    fun `a goal on pace is not flagged and a reached one is good news`() {
        val now = utc(2026, 10, 3)
        val onPace = SavingsGoal(id = "a", name = "Trip", targetAmount = 12_000.0, savedAmount = 6_000.0, monthlySaving = 1_000.0, targetDate = "2027-06-30")
        val reached = SavingsGoal(id = "b", name = "Buffer", targetAmount = 15_000.0, accountId = 2)
        val items = AttentionChecks.run(input(now, goals = listOf(onPace, reached)))
        assertTrue(items.none { it.key.startsWith("goal-behind") })
        assertEquals(AttentionLevel.GOOD, items.single { it.key == "goal-reached:b" }.level)
    }

    @Test
    fun `goal status uses the measured pace once a month has passed`() {
        val status = GoalTracking.status(
            target = 10_000.0, saved = 3_500.0, targetDate = utc(2027, 4, 1),
            startedAt = utc(2026, 8, 1), startAmount = 2_000.0, plannedMonthly = 5_000.0, now = utc(2026, 10, 1)
        )
        assertTrue(status.paceMeasured)
        assertEquals(750.0, status.pacePerMonth!!, 20.0)
        assertTrue(status.behind)
        assertNotNull(status.neededPerMonth)
    }

    @Test
    fun `price rises, new subscriptions and unusual payments are spotted`() {
        val now = utc(2026, 10, 3)
        val txs = listOf(
            tx(utc(2026, 6, 2), 99.0, "Spotify P1"), tx(utc(2026, 7, 2), 99.0, "Spotify P2"),
            tx(utc(2026, 8, 2), 99.0, "Spotify P3"), tx(utc(2026, 9, 2), 119.0, "Spotify P4"),
            tx(utc(2026, 7, 20), 300.0, "Fitness World"), tx(utc(2026, 8, 20), 300.0, "Fitness World"), tx(utc(2026, 9, 20), 300.0, "Fitness World"),
            tx(utc(2026, 7, 5), 250.0, "Netto"), tx(utc(2026, 8, 5), 270.0, "Netto"), tx(utc(2026, 9, 1), 240.0, "Netto"),
            tx(utc(2026, 10, 1), 1900.0, "Netto")
        )
        val keys = AttentionChecks.run(input(now, transactions = txs)).map { it.key }
        assertTrue(keys.toString(), keys.any { it.startsWith("price:spotify") })
        assertTrue(keys.toString(), keys.any { it.startsWith("newsub:fitness world") })
        assertTrue(keys.toString(), keys.any { it.startsWith("unusual:") })
    }

    @Test
    fun `overdraft, expiring bank connection and interest-only end are flagged`() {
        val now = utc(2026, 10, 3)
        val items = AttentionChecks.run(
            input(
                now,
                balances = mapOf(1L to -250.0, 2L to 0.0),
                consents = listOf(BankConsent("Sydbank", utc(2026, 10, 10))),
                loans = listOf(Loan(id = "m", name = "Mortgage", interestOnlyUntil = "01.02.2027"))
            )
        )
        assertEquals(AttentionLevel.URGENT, items.first().level)
        assertTrue(items.any { it.key.startsWith("consent:Sydbank") })
        assertTrue(items.any { it.key.startsWith("io-end:m") })
    }

    @Test
    fun `the monthly review appears in the first week only`() {
        val txs = listOf(tx(utc(2026, 9, 1), 30_000.0, "Salary", null, TransactionType.INCOME), tx(utc(2026, 9, 10), 12_000.0, "Netto"))
        val early = AttentionChecks.run(input(utc(2026, 10, 3), transactions = txs)).singleOrNull { it.key == "review:2026-09" }
        assertNotNull(early)
        assertTrue(early!!.detail, early.detail.contains("saved 18,000.00"))
        assertNull(AttentionChecks.run(input(utc(2026, 10, 12), transactions = txs)).singleOrNull { it.key.startsWith("review:") })
    }

    @Test
    fun `target dates parse in the formats people type`() {
        assertEquals(utc(2027, 6, 30), GoalTracking.parseTargetDate("2027-06-30"))
        assertEquals(utc(2027, 6, 30), GoalTracking.parseTargetDate("2027-06"))
        assertNull(GoalTracking.parseTargetDate("next summer"))
        assertFalse(GoalTracking.parseTargetDate("2027-02") == null)
    }
}
