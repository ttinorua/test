package com.financetracker.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.financetracker.app.data.ai.AiCategorizationWorker
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUi(val account: Account, val balance: Double)

data class SettingsUiState(
    val accounts: List<AccountUi> = emptyList(),
    val categories: List<Category> = emptyList()
)

class SettingsViewModel(private val repository: FinanceRepository, private val appContext: Context) : ViewModel() {

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

    // Runs the AI categorization backfill as WorkManager-managed work (see AiCategorizationWorker)
    // rather than a plain coroutine, so a run that can take a long time survives navigating away,
    // the screen turning off, or the app being backgrounded — its state is read back from
    // WorkManager itself, not held only in this ViewModel's own memory.
    private val workManager = WorkManager.getInstance(appContext)

    private val categorizationWorkInfo: StateFlow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow(AiCategorizationWorker.UNIQUE_WORK_NAME)
        // A build from before this fix could leave more than one WorkInfo entry behind under
        // this same unique name (it used to replace its own still-running unique work from
        // inside doWork(), a known way to leave WorkManager's bookkeeping inconsistent) — always
        // prefer whichever entry is actually still active over just taking the list's first
        // entry, so this never ends up watching a dead, orphaned one while real work (if any)
        // runs under another.
        .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isCategorizing: StateFlow<Boolean> = categorizationWorkInfo
        .map { it != null && !it.state.isFinished }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val categorizationProgress: StateFlow<CategorizationProgress?> = categorizationWorkInfo
        .map { info ->
            if (info == null || info.state.isFinished) return@map null
            val done = info.progress.getInt(AiCategorizationWorker.KEY_DONE, -1)
            val total = info.progress.getInt(AiCategorizationWorker.KEY_TOTAL, -1)
            if (done >= 0 && total > 0) CategorizationProgress(done, total) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _categorizationMessage = MutableStateFlow<String?>(null)
    val categorizationMessage: StateFlow<String?> = _categorizationMessage.asStateFlow()

    init {
        viewModelScope.launch {
            categorizationWorkInfo.collect { info ->
                // A batch that still has work left returns Result.retry(), which WorkManager
                // reports as ENQUEUED again (never SUCCEEDED) until the chain's actual last
                // batch finishes — so any terminal state seen here really is the run's end.
                if (info == null || !info.state.isFinished) return@collect
                _categorizationMessage.value = when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val categorized = info.outputData.getInt(AiCategorizationWorker.KEY_CATEGORIZED, 0)
                        val total = info.outputData.getInt(AiCategorizationWorker.KEY_TOTAL_CONSIDERED, 0)
                        if (total == 0) {
                            "Nothing to categorize — every transaction already has a category."
                        } else {
                            "Categorized $categorized of $total transaction(s)."
                        }
                    }
                    WorkInfo.State.FAILED -> "Categorization failed. Try again."
                    else -> _categorizationMessage.value
                }
                // Otherwise a finished run would keep reappearing every time this ViewModel is
                // recreated (e.g. reopening Settings), since WorkManager keeps finished work
                // around until pruned.
                workManager.pruneWork()
            }
        }
    }

    /** One-time AI backfill for every transaction that has no real category (mainly Enable
     * Banking's history, since it sends no category data). Can take a while for a large
     * history — progress is reported as it goes, and the run keeps going even if this screen
     * is closed. */
    fun categorizeWithAi() {
        if (isCategorizing.value) return
        if (!ClaudeService.isConfigured) {
            _categorizationMessage.value = "Add your Anthropic API key to local.properties and rebuild first."
            return
        }
        _categorizationMessage.value = null
        AiCategorizationWorker.clearPersistedState(appContext)
        // REPLACE (not KEEP): this call only ever happens from here, guarded by the
        // isCategorizing check above, so it can't race with a currently-running worker — it's a
        // deliberate, explicit restart, always giving a clean single work item instead of risking
        // reuse of a leftover/stuck one from a previous run.
        workManager.enqueueUniqueWork(
            AiCategorizationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            AiCategorizationWorker.buildRequest()
        )
    }

    /** Escape hatch for a run that's stuck (e.g. leftover state from a previous app version) —
     * cancels whatever's registered under this unique work name and clears its persisted total,
     * so the next "Categorize with AI" tap is guaranteed a clean start. */
    fun cancelCategorization() {
        workManager.cancelUniqueWork(AiCategorizationWorker.UNIQUE_WORK_NAME)
        AiCategorizationWorker.clearPersistedState(appContext)
        _categorizationMessage.value = null
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
