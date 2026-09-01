package com.example.personalfinance.data

data class TransactionWithCategory(
    val transaction: Transaction,
    val category: Category?
)
