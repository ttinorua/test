package com.financetracker.app.data.db.entity

data class TransactionWithDetails(
    val id: Long,
    val amount: Double,
    val type: TransactionType,
    val accountId: Long,
    val accountName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColorHex: String?,
    val date: Long,
    val note: String
)

data class CategorySpend(
    val categoryId: Long?,
    val categoryName: String,
    val colorHex: String,
    val total: Double
)
