package com.financetracker.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations. Until now the DB relied on [RoomDatabase.Builder.fallbackToDestructiveMigration],
 * which wipes all user data on every schema bump — acceptable while the schema was still settling,
 * but no longer once real data (accounts, transactions, categories) is in the field. New schema
 * versions add a migration here instead so upgrades preserve data.
 *
 * Each `CREATE` mirrors exactly the SQL Room derives from the entities (column order, types,
 * nullability, foreign keys and the default `index_<table>_<column>` index names) so Room's
 * startup schema-identity check passes.
 */

/** v2 → v3: add the Coop receipt connector's tables ([Receipt], [ReceiptLine]). Purely additive —
 *  no existing table is touched, so no data is lost. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`externalId` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`storeName` TEXT NOT NULL, " +
                "`purchasedAt` INTEGER NOT NULL, " +
                "`total` REAL NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`linkedTransactionId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`linkedTransactionId`) REFERENCES `transactions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_receipts_externalId` ON `receipts` (`externalId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_receipts_linkedTransactionId` ON `receipts` (`linkedTransactionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_receipts_purchasedAt` ON `receipts` (`purchasedAt`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `receipt_lines` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`receiptId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unitPrice` REAL NOT NULL, " +
                "`lineTotal` REAL NOT NULL, " +
                "`discount` REAL NOT NULL, " +
                "`deposit` REAL NOT NULL, " +
                "`categoryId` INTEGER, " +
                "FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_receipt_lines_receiptId` ON `receipt_lines` (`receiptId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_receipt_lines_categoryId` ON `receipt_lines` (`categoryId`)"
        )
    }
}

/** Every migration the app ships, in order. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)
