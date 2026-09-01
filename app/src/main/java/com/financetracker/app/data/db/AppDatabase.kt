package com.financetracker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.financetracker.app.data.db.dao.AccountDao
import com.financetracker.app.data.db.dao.CategoryDao
import com.financetracker.app.data.db.dao.TransactionDao
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType

@Database(
    entities = [Account::class, Category::class, Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_tracker.db"
                )
                    .addCallback(SeedCallback(context))
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    private class SeedCallback(private val context: Context) : Callback() {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed a default account and a starter set of categories so the app
            // is immediately usable and imports always have a category to fall back to.
            db.execSQL(
                "INSERT INTO accounts (name, initialBalance, currencyCode) VALUES ('Cash', 0.0, 'USD')"
            )
            val defaultCategories = listOf(
                Triple("Salary", TransactionType.INCOME, "#2E7D32"),
                Triple("Other Income", TransactionType.INCOME, "#66BB6A"),
                Triple("Groceries", TransactionType.EXPENSE, "#EF6C00"),
                Triple("Rent", TransactionType.EXPENSE, "#8D6E63"),
                Triple("Utilities", TransactionType.EXPENSE, "#5C6BC0"),
                Triple("Transportation", TransactionType.EXPENSE, "#26A69A"),
                Triple("Dining", TransactionType.EXPENSE, "#EC407A"),
                Triple("Entertainment", TransactionType.EXPENSE, "#AB47BC"),
                Triple("Healthcare", TransactionType.EXPENSE, "#D32F2F"),
                Triple("Uncategorized", TransactionType.EXPENSE, "#9E9E9E")
            )
            defaultCategories.forEach { (name, type, color) ->
                db.execSQL(
                    "INSERT INTO categories (name, type, colorHex) VALUES ('${name.replace("'", "''")}', '${type.name}', '$color')"
                )
            }
        }
    }
}
