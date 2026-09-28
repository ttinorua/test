package com.financetracker.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("date")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Double,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    /** Epoch millis, UTC midnight for the transaction's calendar date. */
    val date: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
