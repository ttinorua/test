package com.financetracker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.financetracker.app.data.bank.SupportedBanks
import com.financetracker.app.data.db.dao.AccountDao
import com.financetracker.app.data.db.dao.CategoryDao
import com.financetracker.app.data.db.dao.TransactionDao
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction

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
            // Seeded with the default bank's ([SupportedBanks.DEFAULT], Sydbank today) own
            // category taxonomy — the only real backing this app has before the user ever opens
            // Settings > Bank to pick/connect one. See BankCategories.ensure for how an existing
            // install picks up another bank's categories after switching.
            SupportedBanks.DEFAULT.defaultCategories.forEach { seed ->
                db.execSQL(
                    "INSERT INTO categories (name, mainCategory, type, colorHex) VALUES (" +
                        "'${seed.name.replace("'", "''")}', " +
                        "'${seed.mainCategory.replace("'", "''")}', " +
                        "'${seed.type.name}', '${seed.colorHex}')"
                )
            }
        }
    }
}
