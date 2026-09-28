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
    /** [sourceOrderIsNewestFirst] says which end of [importedRows] is most recent among rows
     * sharing the same date, since day-only timestamps can't break the tie themselves — bank
     * spreadsheet exports are typically newest-first (the default), while Enable Banking's API
     * returns transactions oldest-first, so its caller passes false. Getting this backwards
     * silently picks an earlier same-day balance snapshot as ground truth, which is exactly
     * what caused a real reconciled-balance mismatch (grabbing a transaction from a few hours
     * too early on the account's most recent day). */
    suspend fun reconcile(
        repository: FinanceRepository,
        accountId: Long,
        importedRows: List<ParsedTransactionRow>,
        sourceOrderIsNewestFirst: Boolean = true
    ) {
        val rowsWithBalance = importedRows.filter { it.balanceAfter != null }
        if (rowsWithBalance.isEmpty()) return

        val maxDate = rowsWithBalance.maxOf { it.date }
        val referenceRow = if (sourceOrderIsNewestFirst) {
            rowsWithBalance.first { it.date == maxDate }
        } else {
            rowsWithBalance.last { it.date == maxDate }
        }
        val referenceBalance = referenceRow.balanceAfter ?: return

        val allTransactions = repository.getTransactionsForAccount(accountId)
        val deltaUpToReference = allTransactions
            .filter { it.date <= maxDate }
            .sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }

        val account = repository.getAccounts().firstOrNull { it.id == accountId } ?: return
        repository.updateAccount(account.copy(initialBalance = referenceBalance - deltaUpToReference))
    }
}
