package com.financetracker.app.data.repository

import com.financetracker.app.data.db.AppDatabase
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.CategorySpend
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()

    // Accounts
    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()
    suspend fun getAccounts(): List<Account> = accountDao.getAll()
    suspend fun upsertAccount(account: Account): Long = accountDao.insert(account)
    suspend fun updateAccount(account: Account) = accountDao.update(account)
    suspend fun deleteAccount(account: Account) = accountDao.delete(account)

    suspend fun getAccountBalance(account: Account): Double {
        return account.initialBalance + accountDao.getTransactionDelta(account.id)
    }

    suspend fun getOrCreateAccount(name: String): Account {
        return accountDao.getByName(name) ?: run {
            val id = accountDao.insert(Account(name = name))
            Account(id = id, name = name)
        }
    }

    // Categories
    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
    suspend fun getCategories(): List<Category> = categoryDao.getAll()
    suspend fun upsertCategory(category: Category): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    suspend fun getOrCreateCategory(mainCategory: String, name: String, type: TransactionType): Category {
        val trimmedMain = mainCategory.ifBlank { "Uncategorized" }
        val trimmedName = name.ifBlank { "Uncategorized" }
        return categoryDao.getByMainAndNameAndType(trimmedMain, trimmedName, type) ?: run {
            val id = categoryDao.insert(Category(name = trimmedName, mainCategory = trimmedMain, type = type))
            Category(id = id, name = trimmedName, mainCategory = trimmedMain, type = type)
        }
    }

    // Transactions
    fun observeTransactions(): Flow<List<TransactionWithDetails>> =
        transactionDao.observeAllWithDetails()

    fun observeRecentTransactions(limit: Int = 10): Flow<List<TransactionWithDetails>> =
        transactionDao.observeRecentWithDetails(limit)

    suspend fun addTransaction(transaction: Transaction): Long = transactionDao.insert(transaction)

    suspend fun addTransactions(transactions: List<Transaction>): List<Long> =
        transactionDao.insertAll(transactions)

    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

    fun observeIncomeBetween(from: Long, to: Long): Flow<Double> =
        transactionDao.observeIncomeBetween(from, to)

    fun observeExpenseBetween(from: Long, to: Long): Flow<Double> =
        transactionDao.observeExpenseBetween(from, to)

    fun observeNetBalance(): Flow<Double> = transactionDao.observeNetBalance()

    fun observeExpenseByCategoryBetween(from: Long, to: Long): Flow<List<CategorySpend>> =
        transactionDao.observeExpenseByCategoryBetween(from, to)
}
