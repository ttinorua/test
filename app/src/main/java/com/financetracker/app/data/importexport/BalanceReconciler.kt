package com.financetracker.app.data.importexport

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.repository.FinanceRepository

/**
 * A bank export's own running balance (a spreadsheet's "Balance" column, or Enable Banking's
 * `balance_after_transaction`) is ground truth. Backs out the account's opening balance so that
 * opening balance + all deltas reproduces that real balance, instead of leaving the account's
 * balance as whatever arbitrary starting value it had before the import/sync — shared by
 * spreadsheet import and Enable Banking sync so both keep balances correct the same way.
 */
object BalanceReconciler {
    suspend fun reconcile(repository: FinanceRepository, accountId: Long, importedRows: List<ParsedTransactionRow>) {
        val rowsWithBalance = importedRows.filter { it.balanceAfter != null }
        if (rowsWithBalance.isEmpty()) return

        // Among rows sharing the latest date, the source's own ordering (not our day-only
        // timestamp) tells us which one is truly the most recent.
        val maxDate = rowsWithBalance.maxOf { it.date }
        val referenceRow = rowsWithBalance.first { it.date == maxDate }
        val referenceBalance = referenceRow.balanceAfter ?: return

        val allTransactions = repository.getTransactionsForAccount(accountId)
        val deltaUpToReference = allTransactions
            .filter { it.date <= maxDate }
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

        val account = repository.getAccounts().firstOrNull { it.id == accountId } ?: return
        repository.updateAccount(account.copy(initialBalance = referenceBalance - deltaUpToReference))
    }
}
