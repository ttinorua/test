package com.financetracker.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME,
    EXPENSE
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Subcategory label, e.g. "Groceries". */
    val name: String,
    /** Umbrella grouping, e.g. "Food". Used by the spending overview screen. */
    val mainCategory: String = "Uncategorized",
    val type: TransactionType,
    val colorHex: String = "#607D8B"
)
