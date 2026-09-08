package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardTransactionsUiState(
    val label: String = "",
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList()
)

/**
 * Shows every transaction in the dashboard's current period matching [type] and/or
 * [categoryId] (either or both null means "no filter on that field").
 */
class DashboardTransactionsViewModel(
    private val repository: FinanceRepository,
    type: TransactionType?,
    categoryId: Long?,
    label: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?
) : ViewModel() {

    val uiState: StateFlow<DashboardTransactionsUiState> = combine(
        repository.observeTransactions(),
        BudgetSettings.shiftSalaryToNextMonth,
        repository.observeAccounts(),
        repository.observeCategories()
    ) { transactions, shiftSalary, accounts, categories ->
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter { tx ->
            val effectiveDate =
                effectiveReportingDate(tx.date, tx.type, tx.mainCategoryName, tx.categoryName, shiftSalary)
            val inPeriod = effectiveDate >= from && effectiveDate < to
            val matchesType = type == null || tx.type == type
            val matchesCategory = categoryId == null || tx.categoryId == categoryId
            inPeriod && matchesType && matchesCategory
        }.sortedByDescending { it.date }

        DashboardTransactionsUiState(label = label, transactions = filtered, accounts = accounts, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardTransactionsUiState(label = label))

    fun addTransaction(
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(amount = amount, type = type, accountId = accountId, categoryId = categoryId, date = date, note = note)
            )
        }
    }

    fun updateTransaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            repository.updateTransaction(
                Transaction(id = id, amount = amount, type = type, accountId = accountId, categoryId = categoryId, date = date, note = note)
            )
        }
    }

    fun deleteTransaction(transaction: TransactionWithDetails) {
        viewModelScope.launch {
            repository.deleteTransaction(
                Transaction(
                    id = transaction.id,
                    amount = transaction.amount,
                    type = transaction.type,
                    accountId = transaction.accountId,
                    categoryId = transaction.categoryId,
                    date = transaction.date,
                    note = transaction.note
                )
            )
        }
    }
}
