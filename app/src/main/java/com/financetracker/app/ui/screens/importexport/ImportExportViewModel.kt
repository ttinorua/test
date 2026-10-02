package com.financetracker.app.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.BalanceReconciler
import com.financetracker.app.data.importexport.CategoryRestore
import com.financetracker.app.data.importexport.DuplicateTransactionFilter
import com.financetracker.app.data.importexport.FileImportHelper
import com.financetracker.app.data.importexport.ImportResult
import com.financetracker.app.data.importexport.ParsedTransactionRow
import com.financetracker.app.data.importexport.SpreadsheetExporter
import com.financetracker.app.data.importexport.describeError
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat { CSV, XLSX }

data class ImportExportUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: Long? = null,
    val isParsing: Boolean = false,
    val selectedFileName: String? = null,
    val parsedRows: List<ParsedTransactionRow> = emptyList(),
    val parseErrors: List<String> = emptyList(),
    val duplicateCount: Int = 0,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val showResult: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val importError: String? = null,
    val isRestoring: Boolean = false,
    val restoreMessage: String? = null
)

class ImportExportViewModel(
    private val repository: FinanceRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    val selected = state.selectedAccountId?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id
                    state.copy(accounts = accounts, selectedAccountId = selected)
                }
            }
        }
    }

    fun selectAccount(id: Long) {
        _uiState.update { it.copy(selectedAccountId = id) }
    }

    fun onFilePicked(uri: Uri, displayName: String?) {
        val accountId = _uiState.value.selectedAccountId
        _uiState.update {
            it.copy(
                isParsing = true,
                selectedFileName = displayName,
                parsedRows = emptyList(),
                parseErrors = emptyList(),
                duplicateCount = 0,
                showResult = false
            )
        }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { FileImportHelper.parse(appContext, uri) }
            } catch (e: Throwable) {
                ImportResult(emptyList(), listOf("Could not read file: ${describeError(e)}"))
            }

            val (uniqueRows, duplicateCount) = if (accountId != null && result.rows.isNotEmpty()) {
                try {
                    withContext(Dispatchers.IO) { filterOutDuplicates(accountId, result.rows) }
                } catch (e: Throwable) {
                    result.rows to 0
                }
            } else {
                result.rows to 0
            }

            _uiState.update {
                it.copy(
                    isParsing = false,
                    parsedRows = uniqueRows,
                    parseErrors = result.errors,
                    duplicateCount = duplicateCount
                )
            }
        }
    }

    /** Skips rows that already exist for this account, so re-importing an overlapping or
     * previously-uploaded file never double-counts transactions. */
    private suspend fun filterOutDuplicates(
        accountId: Long,
        rows: List<ParsedTransactionRow>
    ): Pair<List<ParsedTransactionRow>, Int> {
        val existing = repository.getTransactionsForAccount(accountId)
        val result = DuplicateTransactionFilter.filter(existing, rows)
        return result.uniqueRows to result.duplicateCount
    }

    fun cancelPreview() {
        _uiState.update {
            it.copy(parsedRows = emptyList(), parseErrors = emptyList(), duplicateCount = 0, selectedFileName = null)
        }
    }

    fun confirmImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        if (state.parsedRows.isEmpty() || state.isImporting) return

        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val categoryCache = mutableMapOf<Triple<String, String, TransactionType>, Long>()
                val transactions = state.parsedRows.map { row ->
                    val key = Triple(row.mainCategoryName, row.categoryName, row.type)
                    val categoryId = categoryCache.getOrPut(key) {
                        repository.getOrCreateCategory(row.mainCategoryName, row.categoryName, row.type).id
                    }
                    Transaction(
                        amount = row.amount,
                        type = row.type,
                        accountId = accountId,
                        categoryId = categoryId,
                        date = row.date,
                        note = row.note
                    )
                }
                repository.addTransactions(transactions)
                BalanceReconciler.reconcile(repository, accountId, state.parsedRows)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importedCount = transactions.size,
                        skippedDuplicates = state.duplicateCount,
                        showResult = true,
                        parsedRows = emptyList(),
                        parseErrors = emptyList(),
                        duplicateCount = 0,
                        selectedFileName = null
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importError = "Import failed: ${describeError(e)}"
                    )
                }
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(showResult = false, importedCount = 0, skippedDuplicates = 0) }
    }

    fun exportTransactions(uri: Uri, format: ExportFormat) {
        _uiState.update { it.copy(isExporting = true, exportMessage = null) }
        viewModelScope.launch {
            val transactions = repository.observeTransactions().first()
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    when (format) {
                        ExportFormat.CSV -> SpreadsheetExporter.exportCsv(transactions, out)
                        ExportFormat.XLSX -> SpreadsheetExporter.exportXlsx(transactions, out)
                    }
                }
            }
            _uiState.update {
                it.copy(isExporting = false, exportMessage = "Exported ${transactions.size} transactions")
            }
        }
    }

    fun dismissExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    /** Re-applies the categories from one of the app's own exported spreadsheets onto the
     * matching transactions already in the app (see [CategoryRestore]) — the way to get a
     * category history back after reinstalling and re-syncing from the bank. */
    fun restoreCategories(uri: Uri) {
        if (_uiState.value.isRestoring) return
        _uiState.update { it.copy(isRestoring = true, restoreMessage = null) }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val parsed = FileImportHelper.parse(appContext, uri)
                    if (parsed.rows.isEmpty()) {
                        "No transactions found in that file." +
                            (parsed.errors.firstOrNull()?.let { " $it" } ?: "")
                    } else {
                        val plan = CategoryRestore.plan(repository.getAllTransactions(), parsed.rows)
                        val changed = applyRestore(plan)
                        buildString {
                            append("Restored categories for $changed transactions.")
                            val unchanged = plan.matches.size - changed
                            if (unchanged > 0) append(" $unchanged already had the right category.")
                            if (plan.unmatchedCount > 0) {
                                append(" ${plan.unmatchedCount} from the backup weren't found in the app (e.g. history not synced yet).")
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                "Restore failed: ${describeError(e)}"
            }
            _uiState.update { it.copy(isRestoring = false, restoreMessage = message) }
        }
    }

    /** Returns how many transactions actually changed category. An existing category with the
     * same main + name is reused whatever its income/expense type (e.g. an incoming transfer
     * categorized "Other (Transfer)"), rather than creating a duplicate of it. */
    private suspend fun applyRestore(plan: CategoryRestore.Plan): Int {
        val categories = repository.getCategories()
        val resolved = mutableMapOf<Triple<String, String, TransactionType>, Long>()
        var changed = 0
        for ((transaction, row) in plan.matches) {
            val categoryId = resolved.getOrPut(Triple(row.mainCategoryName, row.categoryName, row.type)) {
                categories.firstOrNull {
                    it.mainCategory.equals(row.mainCategoryName.trim(), ignoreCase = true) &&
                        it.name.equals(row.categoryName.trim(), ignoreCase = true) &&
                        it.type == row.type
                }?.id ?: categories.firstOrNull {
                    it.mainCategory.equals(row.mainCategoryName.trim(), ignoreCase = true) &&
                        it.name.equals(row.categoryName.trim(), ignoreCase = true)
                }?.id ?: repository.getOrCreateCategory(row.mainCategoryName.trim(), row.categoryName.trim(), row.type).id
            }
            if (transaction.categoryId != categoryId) {
                repository.updateTransaction(transaction.copy(categoryId = categoryId))
                changed++
            }
        }
        return changed
    }

    fun dismissRestoreMessage() {
        _uiState.update { it.copy(restoreMessage = null) }
    }

    fun dismissImportError() {
        _uiState.update { it.copy(importError = null) }
    }
}
