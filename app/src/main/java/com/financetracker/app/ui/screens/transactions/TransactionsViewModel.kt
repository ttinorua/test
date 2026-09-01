package com.financetracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList()
)

class TransactionsViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.observeTransactions(),
        repository.observeAccounts(),
        repository.observeCategories()
    ) { transactions, accounts, categories ->
        TransactionsUiState(transactions, accounts, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

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
                Transaction(
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    note = note
                )
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
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    note = note
                )
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
