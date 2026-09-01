package com.example.personalfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    /** ARGB color used to render this category's icon background. */
    val color: Long,
    /** Key into [com.example.personalfinance.ui.common.CategoryIcons]. */
    val icon: String,
    val isDefault: Boolean = false
)
