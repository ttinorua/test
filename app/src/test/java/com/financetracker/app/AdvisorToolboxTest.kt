package com.financetracker.app

import com.financetracker.app.data.ai.LoanDocumentReader
import com.financetracker.app.data.ai.advisor.ActionPlan
import com.financetracker.app.data.ai.advisor.AdvisorSnapshot
import com.financetracker.app.data.ai.advisor.AdvisorToolbox
import com.financetracker.app.data.ai.agent.ToolCall
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.ui.screens.settings.parseNumber
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AdvisorToolboxTest {

    private fun utc(year: Int, month: Int, day: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day)
    }.timeInMillis

    private val groceries = Category(id = 1, name = "Groceries", mainCategory = "Food", type = TransactionType.EXPENSE)
    private val uncategorized = Category(id = 2, name = "Uncategorized", mainCategory = "Uncategorized", type = TransactionType.EXPENSE)
    private val salary = Category(id = 3, name = "Salary", mainCategory = "Income", type = TransactionType.INCOME)
    private val categories = listOf(groceries, uncategorized, salary)

    private var nextId = 1L
    private fun tx(date: Long, amount: Double, note: String, category: Category?, type: TransactionType = TransactionType.EXPENSE) =
        TransactionWithDetails(
            id = nextId++, amount = amount, type = type, accountId = 1, accountName = "Main",
            categoryId = category?.id, categoryName = category?.name, mainCategoryName = category?.mainCategory,
            categoryColorHex = null, date = date, note = note
        )

    private val transactions = listOf(
        tx(utc(2026, 9, 3), 412.50, "Netto Aarhus", groceries),
        tx(utc(2026, 9, 10), 250.00, "NETTO 1234", groceries),
        tx(utc(2026, 9, 12), 80.00, "MobilePay Kiosk", null),
        tx(utc(2026, 9, 20), 120.00, "MobilePay Kiosk", uncategorized),
        tx(utc(2026, 9, 30), 30000.00, "Salary", salary, TransactionType.INCOME),
        tx(utc(2026, 10, 1), 99.00, "Rema 1000", groceries)
    )

    private fun toolbox() = AdvisorToolbox(
        AdvisorSnapshot(
            transactions = transactions,
            accounts = listOf(Account(id = 1, name = "Main")),
            balances = mapOf(1L to 10_000.0),
            categories = categories,
            now = utc(2026, 10, 3)
        )
    )

    private fun call(box: AdvisorToolbox, name: String, args: JSONObject = JSONObject()) =
        runBlocking { box.execute(ToolCall("1", name, args)) }

    @Test
    fun `find_transactions searches text and counts uncategorized both ways`() {
        val box = toolbox()
        val netto = call(box, "find_transactions", JSONObject().put("text", "netto"))
        assertTrue(netto, netto.startsWith("Found 2 transactions"))
        val open = call(box, "find_transactions", JSONObject().put("uncategorized", true))
        assertTrue(open, open.startsWith("Found 2 transactions"))
    }

    @Test
    fun `get_spending totals a month by category`() {
        val result = call(toolbox(), "get_spending", JSONObject().put("from", "2026-09").put("to", "2026-09"))
        assertTrue(result, result.contains("total 862.50"))
        assertTrue(result, result.contains("Food/Groceries: 662.50"))
        assertTrue(result, result.contains("Uncategorized: 200.00"))
    }

    @Test
    fun `recategorize prepares a card and changes nothing yet`() {
        val box = toolbox()
        val result = call(box, "recategorize", JSONObject().put("match_text", "mobilepay").put("to_category", "Groceries"))
        assertTrue(result, result.contains("Nothing has changed yet"))
        val plan = box.cards.single().plan as ActionPlan.Recategorize
        assertEquals(listOf(3L, 4L), plan.assignments.single().transactionIds)
        assertEquals(1L, plan.assignments.single().categoryId)
        assertNull(plan.newCategory)
    }

    @Test
    fun `recategorize to a missing category creates it on Apply`() {
        val box = toolbox()
        call(box, "recategorize", JSONObject().put("match_text", "kiosk").put("to_category", "Snacks").put("to_main_category", "Food"))
        val plan = box.cards.single().plan as ActionPlan.Recategorize
        assertEquals("Snacks", plan.newCategory?.name)
        assertEquals(TransactionType.EXPENSE, plan.newCategory?.type)
        assertNull(plan.assignments.single().categoryId)
    }

    @Test
    fun `set_budgets resolves categories and 0 removes`() {
        val box = toolbox()
        call(
            box, "set_budgets",
            JSONObject().put("overall_amount", 0).put(
                "category_budgets", JSONArray().put(JSONObject().put("category", "Food/Groceries").put("amount", 2500))
            )
        )
        val plan = box.cards.single().plan as ActionPlan.SetBudgets
        assertTrue(plan.setOverall)
        assertNull(plan.overall)
        assertEquals(mapOf(1L to 2500.0), plan.categories)
    }

    @Test
    fun `a failed attempt's cards are dropped on reset`() {
        val box = toolbox()
        call(box, "show_transactions", JSONObject().put("text", "netto"))
        assertEquals(1, box.cards.size)
        box.reset()
        assertTrue(box.cards.isEmpty())
    }

    @Test
    fun `loan calculator uses the exact annuity maths`() {
        val result = call(
            toolbox(), "loan_calculator",
            JSONObject().put("principal", 2_000_000).put("annual_rate_pct", 4).put("years", 30).put("extra_monthly", 1000)
        )
        assertTrue(result, result.contains("Monthly payment: 9,548.3"))
        assertTrue(result, result.contains("months sooner"))
    }

    @Test
    fun `unknown functions and bad input come back as errors, not crashes`() {
        val box = toolbox()
        assertTrue(call(box, "delete_everything").startsWith("Error"))
        assertTrue(call(box, "get_spending", JSONObject().put("account", "Nope")).startsWith("Error"))
        assertTrue(call(box, "recategorize", JSONObject().put("to_category", "Groceries")).startsWith("Error"))
        assertTrue(box.cards.isEmpty())
    }

    @Test
    fun `document reader output becomes a loan without personal numbers`() {
        val loan = LoanDocumentReader.toLoan(
            """Here you go: {"name": "House mortgage", "kind": "mortgage", "remaining_debt": 1834500.5,
               "interest_rate_pct": 4.0, "contribution_rate_pct": 0.65, "rate_type": "fixed",
               "other_terms": "Loan no. 1234 5678901234, CPR 010190-1234", "summary": null}"""
        )
        assertEquals("House mortgage", loan.name)
        assertEquals(1834500.5, loan.remainingDebt!!, 0.001)
        assertEquals(0.65, loan.contributionRatePct!!, 0.0001)
        assertFalse(loan.otherTerms, loan.otherTerms.contains("5678901234"))
        assertFalse(loan.otherTerms, loan.otherTerms.contains("010190"))
        assertEquals("", loan.summary)
    }

    @Test
    fun `number fields accept Danish and English formats`() {
        assertEquals(1234567.89, parseNumber("1.234.567,89")!!, 0.001)
        assertEquals(1234567.89, parseNumber("1,234,567.89")!!, 0.001)
        assertEquals(4.5, parseNumber("4,5")!!, 0.001)
        assertEquals(2000000.0, parseNumber("2.000.000")!!, 0.001)
        assertNull(parseNumber("abc"))
    }
}
