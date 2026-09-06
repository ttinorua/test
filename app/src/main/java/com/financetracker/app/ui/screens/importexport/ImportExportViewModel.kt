package com.financetracker.app.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.FileImportHelper
import com.financetracker.app.data.importexport.ParsedTransactionRow
import com.financetracker.app.data.importexport.SpreadsheetExporter
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
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    val showResult: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null
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
        _uiState.update {
            it.copy(
                isParsing = true,
                selectedFileName = displayName,
                parsedRows = emptyList(),
                parseErrors = emptyList(),
                showResult = false
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { FileImportHelper.parse(appContext, uri) }
            _uiState.update { it.copy(isParsing = false, parsedRows = result.rows, parseErrors = result.errors) }
        }
    }

    fun cancelPreview() {
        _uiState.update { it.copy(parsedRows = emptyList(), parseErrors = emptyList(), selectedFileName = null) }
    }

    fun confirmImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        if (state.parsedRows.isEmpty() || state.isImporting) return

        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
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
            reconcileAccountBalance(accountId, state.parsedRows)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importedCount = transactions.size,
                    showResult = true,
                    parsedRows = emptyList(),
                    parseErrors = emptyList(),
                    selectedFileName = null
                )
            }
        }
    }

    /**
     * A bank export's own running "Balance" column is ground truth. Use it to back out the
     * account's opening balance so that opening balance + all deltas reproduces the bank's
     * real current balance, instead of leaving the account's balance as whatever arbitrary
     * starting value it had before the import (which is what was causing wildly wrong totals).
     */
    private suspend fun reconcileAccountBalance(accountId: Long, importedRows: List<ParsedTransactionRow>) {
        val rowsWithBalance = importedRows.filter { it.balanceAfter != null }
        if (rowsWithBalance.isEmpty()) return

        // The file lists transactions in its own chronological direction; among rows sharing
        // the latest date, the file's own ordering (not our day-only timestamp) tells us which
        // one is truly the most recent.
        val maxDate = rowsWithBalance.maxOf { it.date }
        val referenceRow = rowsWithBalance.first { it.date == maxDate }
        val referenceBalance = referenceRow.balanceAfter ?: return

        val allTransactions = repository.getTransactionsForAccount(accountId)
        val deltaUpToReference = allTransactions
            .filter { it.date <= maxDate }
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

        val account = _uiState.value.accounts.firstOrNull { it.id == accountId } ?: return
        repository.updateAccount(account.copy(initialBalance = referenceBalance - deltaUpToReference))
    }

    fun dismissResult() {
        _uiState.update { it.copy(showResult = false, importedCount = 0) }
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
}
