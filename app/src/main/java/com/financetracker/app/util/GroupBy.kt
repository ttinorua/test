package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionWithDetails

enum class GroupByOption(val label: String) {
    ACCOUNT("Account"),
    MAIN_CATEGORY("Main category"),
    CATEGORY("Category")
}

fun groupKeyOf(tx: TransactionWithDetails, groupBy: GroupByOption): String = when (groupBy) {
    GroupByOption.ACCOUNT -> tx.accountName
    GroupByOption.MAIN_CATEGORY -> tx.mainCategoryName ?: "Uncategorized"
    GroupByOption.CATEGORY -> tx.categoryName ?: "Uncategorized"
}
