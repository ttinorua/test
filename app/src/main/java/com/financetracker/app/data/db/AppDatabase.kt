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
    version = 2,
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
                    // Pre-release app, schema is still settling; wipe and reseed on
                    // version bumps instead of hand-writing migrations for now.
                    .fallbackToDestructiveMigration()
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
                "INSERT INTO accounts (name, initialBalance, currencyCode) VALUES ('Cash', 0.0, 'DKK')"
            )
            val defaultCategories = listOf(
                CategorySeed("Salary", "Income", TransactionType.INCOME, "#2E7D32"),
                CategorySeed("Other Income", "Income", TransactionType.INCOME, "#66BB6A"),
                CategorySeed("Groceries", "Food", TransactionType.EXPENSE, "#EF6C00"),
                CategorySeed("Rent", "Home", TransactionType.EXPENSE, "#8D6E63"),
                CategorySeed("Utilities", "Home", TransactionType.EXPENSE, "#5C6BC0"),
                CategorySeed("Transportation", "Transportation", TransactionType.EXPENSE, "#26A69A"),
                CategorySeed("Dining", "Leisure", TransactionType.EXPENSE, "#EC407A"),
                CategorySeed("Entertainment", "Leisure", TransactionType.EXPENSE, "#AB47BC"),
                CategorySeed("Healthcare", "Clothing and pers. care prod.", TransactionType.EXPENSE, "#D32F2F"),
                CategorySeed("Uncategorized", "Uncategorized", TransactionType.EXPENSE, "#9E9E9E")
            )
            defaultCategories.forEach { seed ->
                db.execSQL(
                    "INSERT INTO categories (name, mainCategory, type, colorHex) VALUES (" +
                        "'${seed.name.replace("'", "''")}', " +
                        "'${seed.mainCategory.replace("'", "''")}', " +
                        "'${seed.type.name}', '${seed.colorHex}')"
                )
            }
        }
    }

    private data class CategorySeed(
        val name: String,
        val mainCategory: String,
        val type: TransactionType,
        val colorHex: String
    )
}
