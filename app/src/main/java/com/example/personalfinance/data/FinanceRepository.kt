package com.example.personalfinance.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    val categories: Flow<List<Category>> = categoryDao.getAll()

    val transactionsWithCategory: Flow<List<TransactionWithCategory>> =
        combine(transactionDao.getAll(), categoryDao.getAll()) { transactions, categories ->
            val categoriesById = categories.associateBy { it.id }
            transactions.map { transaction ->
                TransactionWithCategory(transaction, categoriesById[transaction.categoryId])
            }
        }

    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.all)
        }
    }

    suspend fun getTransaction(id: Long): Transaction? = transactionDao.getById(id)

    suspend fun addTransaction(transaction: Transaction): Long = transactionDao.insert(transaction)

    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

    suspend fun addCategory(category: Category): Long = categoryDao.insert(category)

    suspend fun updateCategory(category: Category) = categoryDao.update(category)

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}
