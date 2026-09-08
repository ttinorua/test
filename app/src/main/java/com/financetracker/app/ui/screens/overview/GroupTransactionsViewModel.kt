package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.groupKeyOf
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupTransactionsUiState(
    val groupLabel: String = "",
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList()
)

/** Shows every transaction in [key]'s [groupBy] bucket for the period the Spending tab was on. */
class GroupTransactionsViewModel(
    private val repository: FinanceRepository,
    groupBy: GroupByOption,
    key: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?
) : ViewModel() {

    val uiState: StateFlow<GroupTransactionsUiState> = combine(
        repository.observeTransactions(),
        BudgetSettings.shiftSalaryToNextMonth,
        repository.observeAccounts(),
        repository.observeCategories()
    ) { transactions, shiftSalary, accounts, categories ->
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            effectiveDate >= from && effectiveDate < to && groupKeyOf(it, groupBy) == key
        }.sortedByDescending { it.date }

        GroupTransactionsUiState(groupLabel = key, transactions = filtered, accounts = accounts, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupTransactionsUiState(groupLabel = key))

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
