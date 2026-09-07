package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.CategorySpend
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.BudgetLimits
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** null/null = no filter (show everything in the period). */
data class DashboardSelection(
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val label: String? = null
)

data class CategoryBudgetStatus(
    val categoryId: Long,
    val categoryName: String,
    val mainCategory: String,
    val colorHex: String,
    val budget: Double,
    val spent: Double
)

data class BudgetStatus(
    val overallBudget: Double? = null,
    val overallSpent: Double = 0.0,
    val categoryStatuses: List<CategoryBudgetStatus> = emptyList()
)

data class DashboardUiState(
    val netBalance: Double = 0.0,
    val periodIncome: Double = 0.0,
    val periodExpense: Double = 0.0,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val transactions: List<TransactionWithDetails> = emptyList(),
    val periodOption: PeriodOption = PeriodOption.THIS_MONTH,
    val customRange: Pair<Long, Long>? = null,
    val selection: DashboardSelection = DashboardSelection(),
    val budgetStatus: BudgetStatus = BudgetStatus()
)

private const val UNFILTERED_DISPLAY_LIMIT = 20

private data class DashboardFilters(
    val periodOption: PeriodOption,
    val customRange: Pair<Long, Long>?,
    val selection: DashboardSelection
)

private data class DashboardExtras(
    val shiftSalary: Boolean,
    val categories: List<Category>,
    val overallBudget: Double?,
    val categoryBudgets: Map<Long, Double>
)

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _periodOption = MutableStateFlow(PeriodOption.THIS_MONTH)
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _selection = MutableStateFlow(DashboardSelection())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeTransactions(),
        repository.observeNetBalance(),
        combine(_periodOption, _customRange, _selection) { periodOption, customRange, selection ->
            DashboardFilters(periodOption, customRange, selection)
        },
        combine(
            BudgetSettings.shiftSalaryToNextMonth,
            repository.observeCategories(),
            BudgetLimits.overallMonthlyBudget,
            BudgetLimits.categoryBudgets
        ) { shiftSalary, categories, overallBudget, categoryBudgets ->
            DashboardExtras(shiftSalary, categories, overallBudget, categoryBudgets)
        }
    ) { transactions, netBalance, filters, extras ->
        val (periodOption, customRange, selection) = filters
        val (shiftSalary, categories, overallBudget, categoryBudgets) = extras
        val (from, to) = periodRange(periodOption, customRange)
        val inPeriod = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            effectiveDate >= from && effectiveDate < to
        }

        val income = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = inPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val breakdown = inPeriod
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (categoryId, txs) ->
                val sample = txs.first()
                CategorySpend(
                    mainCategory = sample.mainCategoryName ?: "Uncategorized",
                    categoryId = categoryId,
                    categoryName = sample.categoryName ?: "Uncategorized",
                    colorHex = sample.categoryColorHex ?: "#9E9E9E",
                    total = txs.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.total }

        val matches = inPeriod.filter { tx ->
            when {
                selection.categoryId != null -> tx.categoryId == selection.categoryId
                selection.type != null -> tx.type == selection.type
                else -> true
            }
        }.sortedByDescending { it.date }

        val isFiltered = selection.type != null || selection.categoryId != null
        val displayed = if (isFiltered) matches else matches.take(UNFILTERED_DISPLAY_LIMIT)

        val (monthFrom, monthTo) = periodRange(PeriodOption.THIS_MONTH, null)
        val monthExpenses = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            it.type == TransactionType.EXPENSE && effectiveDate >= monthFrom && effectiveDate < monthTo
        }
        val spentByCategory = monthExpenses
            .filter { it.categoryId != null }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        val categoryById = categories.associateBy { it.id }
        val categoryStatuses = categoryBudgets.mapNotNull { (categoryId, budget) ->
            val category = categoryById[categoryId] ?: return@mapNotNull null
            CategoryBudgetStatus(
                categoryId = categoryId,
                categoryName = category.name,
                mainCategory = category.mainCategory,
                colorHex = category.colorHex,
                budget = budget,
                spent = spentByCategory[categoryId] ?: 0.0
            )
        }.sortedByDescending { if (it.budget > 0) it.spent / it.budget else 0.0 }

        DashboardUiState(
            netBalance = netBalance,
            periodIncome = income,
            periodExpense = expense,
            categoryBreakdown = breakdown,
            transactions = displayed,
            periodOption = periodOption,
            customRange = customRange,
            selection = selection,
            budgetStatus = BudgetStatus(
                overallBudget = overallBudget,
                overallSpent = monthExpenses.sumOf { it.amount },
                categoryStatuses = categoryStatuses
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectPeriod(option: PeriodOption) {
        _periodOption.value = option
    }

    fun selectCustomRange(start: Long, endExclusive: Long) {
        _customRange.value = start to endExclusive
        _periodOption.value = PeriodOption.CUSTOM
    }

    fun selectIncome() {
        _selection.update {
            if (it.type == TransactionType.INCOME && it.categoryId == null) DashboardSelection()
            else DashboardSelection(type = TransactionType.INCOME, label = "Income")
        }
    }

    fun selectExpense() {
        _selection.update {
            if (it.type == TransactionType.EXPENSE && it.categoryId == null) DashboardSelection()
            else DashboardSelection(type = TransactionType.EXPENSE, label = "Expenses")
        }
    }

    fun selectCategory(categoryId: Long?, label: String) {
        _selection.update {
            if (it.categoryId == categoryId) DashboardSelection()
            else DashboardSelection(type = TransactionType.EXPENSE, categoryId = categoryId, label = label)
        }
    }

    fun clearSelection() {
        _selection.value = DashboardSelection()
    }

    private val _isGeneratingInsights = MutableStateFlow(false)
    val isGeneratingInsights: StateFlow<Boolean> = _isGeneratingInsights.asStateFlow()

    fun generateInsights() {
        if (_isGeneratingInsights.value) return
        _isGeneratingInsights.value = true

        viewModelScope.launch {
            val current = uiState.value
            val currencyCode = CurrencySettings.currencyCode.value
            val systemPrompt =
                "You are a friendly personal finance assistant embedded in the user's finance-tracking app. " +
                    "In 2-3 short sentences, give one specific, useful observation about their spending this " +
                    "period using the numbers provided. Be concrete with amounts and category names. Avoid " +
                    "generic advice like 'track your spending' or 'create a budget'."
            val summary = buildString {
                appendLine("Currency: $currencyCode")
                appendLine("Period income: ${current.periodIncome}")
                appendLine("Period expense: ${current.periodExpense}")
                appendLine("Spending by category (mainCategory / category: total):")
                current.categoryBreakdown.forEach {
                    appendLine("- ${it.mainCategory} / ${it.categoryName}: ${it.total}")
                }
            }

            ClaudeService.ask(systemPrompt, summary, maxTokens = 300L)
                .onSuccess { AiInsightsCache.save(it) }
                .onFailure { AiInsightsCache.save("Couldn't generate insights: ${it.message}") }
            _isGeneratingInsights.value = false
        }
    }
}
