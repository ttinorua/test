package com.financetracker.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.ai.AiCategorizationCoordinator
import com.financetracker.app.data.ai.CategorizationProgress
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing.asStateFlow()

    private val _categorizationProgress = MutableStateFlow<CategorizationProgress?>(null)
    val categorizationProgress: StateFlow<CategorizationProgress?> = _categorizationProgress.asStateFlow()

    private val _categorizationMessage = MutableStateFlow<String?>(null)
    val categorizationMessage: StateFlow<String?> = _categorizationMessage.asStateFlow()

    /** One-time AI backfill for every transaction that has no real category (mainly Enable
     * Banking's history, since it sends no category data). Can take a while for a large
     * history — progress is reported as it goes. */
    fun categorizeWithAi() {
        if (_isCategorizing.value) return
        if (!ClaudeService.isConfigured) {
            _categorizationMessage.value = "Add your Anthropic API key to local.properties and rebuild first."
            return
        }
        _isCategorizing.value = true
        _categorizationMessage.value = null
        _categorizationProgress.value = null
        viewModelScope.launch {
            val outcome = AiCategorizationCoordinator.categorizeUncategorized(repository) { progress ->
                _categorizationProgress.value = progress
            }
            _categorizationMessage.value = if (outcome.totalConsidered == 0) {
                "Nothing to categorize — every transaction already has a category."
            } else {
                "Categorized ${outcome.categorizedCount} of ${outcome.totalConsidered} transaction(s)."
            }
            _isCategorizing.value = false
            _categorizationProgress.value = null
        }
    }

    fun dismissCategorizationMessage() {
        _categorizationMessage.value = null
    }

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
