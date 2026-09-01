package com.example.personalfinance.data

/** Seeded on first launch so the app is usable before the user creates anything. */
object DefaultCategories {
    val all = listOf(
        Category(name = "Salary", type = TransactionType.INCOME, color = 0xFF2E7D32, icon = "salary", isDefault = true),
        Category(name = "Gifts", type = TransactionType.INCOME, color = 0xFF00897B, icon = "gift", isDefault = true),
        Category(name = "Other Income", type = TransactionType.INCOME, color = 0xFF558B2F, icon = "trending_up", isDefault = true),
        Category(name = "Food", type = TransactionType.EXPENSE, color = 0xFFEF6C00, icon = "food", isDefault = true),
        Category(name = "Transport", type = TransactionType.EXPENSE, color = 0xFF1565C0, icon = "transport", isDefault = true),
        Category(name = "Shopping", type = TransactionType.EXPENSE, color = 0xFF8E24AA, icon = "shopping", isDefault = true),
        Category(name = "Bills & Utilities", type = TransactionType.EXPENSE, color = 0xFFC62828, icon = "bills", isDefault = true),
        Category(name = "Entertainment", type = TransactionType.EXPENSE, color = 0xFFD81B60, icon = "entertainment", isDefault = true),
        Category(name = "Health", type = TransactionType.EXPENSE, color = 0xFF00838F, icon = "health", isDefault = true),
        Category(name = "Housing", type = TransactionType.EXPENSE, color = 0xFF6D4C41, icon = "housing", isDefault = true),
        Category(name = "Other", type = TransactionType.EXPENSE, color = 0xFF546E7A, icon = "other", isDefault = true)
    )
}
