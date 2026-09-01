package com.financetracker.app.data.db

import androidx.room.TypeConverter
import com.financetracker.app.data.db.entity.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
}
