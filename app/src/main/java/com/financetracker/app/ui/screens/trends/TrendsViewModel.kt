package com.financetracker.app.ui.screens.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.effectiveReportingDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class TrendsWindow(val months: Int, val label: String) {
    SIX(6, "6 months"),
    TWELVE(12, "12 months")
}

data class MonthlyTrend(
    val year: Int,
    val month: Int,
    val monthLabel: String,
    val income: Double,
    val expense: Double
) {
    val net: Double get() = income - expense
}

data class TrendsUiState(
    val window: TrendsWindow = TrendsWindow.SIX,
    val trends: List<MonthlyTrend> = emptyList(),
    val avgIncome: Double = 0.0,
    val avgExpense: Double = 0.0,
    val bestMonth: MonthlyTrend? = null,
    val worstMonth: MonthlyTrend? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: Long? = null
)

/** Monthly income/expense/net for the last N calendar months, across all accounts or one. */
class TrendsViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _window = MutableStateFlow(TrendsWindow.SIX)
    private val _selectedAccountId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TrendsUiState> = combine(
        repository.observeTransactions(),
        repository.observeAccounts(),
        BudgetSettings.shiftSalaryToNextMonth,
        BudgetSettings.excludeTransfersFromSpending,
        combine(_window, _selectedAccountId) { window, selectedAccountId -> window to selectedAccountId }
    ) { allTransactions, accounts, shiftSalary, excludeTransfers, windowAndAccount ->
        val (window, selectedAccountId) = windowAndAccount
        val transactions = if (selectedAccountId != null) {
            allTransactions.filter { it.accountId == selectedAccountId }
        } else {
            allTransactions
        }
        val trends = lastNMonths(window.months).map { (year, month, label) ->
            val (from, to) = monthRange(year, month)
            val inMonth = transactions.filter {
                val effectiveDate =
                    effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
                effectiveDate >= from && effectiveDate < to
            }
            MonthlyTrend(
                year = year,
                month = month,
                monthLabel = label,
                income = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = inMonth.filter {
                    it.type == TransactionType.EXPENSE &&
                        countsTowardSpending(it.type, it.mainCategoryName, it.categoryName, excludeTransfers)
                }.sumOf { it.amount }
            )
        }

        TrendsUiState(
            window = window,
            trends = trends,
            avgIncome = trends.map { it.income }.average().takeIf { it.isFinite() } ?: 0.0,
            avgExpense = trends.map { it.expense }.average().takeIf { it.isFinite() } ?: 0.0,
            bestMonth = trends.maxByOrNull { it.net },
            worstMonth = trends.minByOrNull { it.net },
            accounts = accounts,
            selectedAccountId = selectedAccountId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    fun selectWindow(window: TrendsWindow) {
        _window.value = window
    }

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    /** Oldest to newest, ending with the current calendar month. */
    private fun lastNMonths(count: Int): List<Triple<Int, Int, String>> {
        val tz = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(tz).apply { set(Calendar.DAY_OF_MONTH, 1) }
        cal.add(Calendar.MONTH, -(count - 1))
        val monthFormat = SimpleDateFormat("MMM", Locale.US).apply { timeZone = tz }

        return (0 until count).map {
            val entry = Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), monthFormat.format(cal.time))
            cal.add(Calendar.MONTH, 1)
            entry
        }
    }

    private fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val tz = TimeZone.getTimeZone("UTC")
        val start = Calendar.getInstance(tz).apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }
}
