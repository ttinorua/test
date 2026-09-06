package com.financetracker.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUi(val account: Account, val balance: Double)

data class SettingsUiState(
    val accounts: List<AccountUi> = emptyList(),
    val categories: List<Category> = emptyList()
)

class SettingsViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeAccounts(),
        repository.observeTransactions(),
        repository.observeCategories()
    ) { accounts, transactions, categories ->
        val accountUis = accounts.map { acc ->
            val delta = transactions.filter { it.accountId == acc.id }
                .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
            AccountUi(acc, acc.initialBalance + delta)
        }
        SettingsUiState(accountUis, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addAccount(name: String, initialBalance: Double) {
        viewModelScope.launch { repository.upsertAccount(Account(name = name, initialBalance = initialBalance)) }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { repository.updateAccount(account) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repository.deleteAccount(account) }
    }

    fun addCategory(mainCategory: String, name: String, type: TransactionType) {
        viewModelScope.launch {
            repository.upsertCategory(Category(name = name, mainCategory = mainCategory, type = type))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }
}
