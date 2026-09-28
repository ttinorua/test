package com.financetracker.app.data.bank

import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType

/**
 * The app's own original hand-picked category names (from before [BankdataDefaultCategories]
 * replaced them with the real per-bank taxonomy) that don't have one single, safe real-taxonomy
 * equivalent to rename to — each one used to lump together several distinct real categories (old
 * "Transportation" could mean Fuel, Parking, Taxis and public transportation, Service and repair,
 * and more), so blindly reassigning every transaction under one of these to a single new category
 * would misclassify most of them. [com.financetracker.app.data.ai.AiCategorizationCoordinator]
 * instead treats a transaction still sitting under one of these the same as a
 * literally-Uncategorized one: eligible to be individually re-matched against the real taxonomy
 * by its own note text, the same pipeline that already replaced most of the guessed defaults with
 * real categories. Left in place, never deleted, as a legitimate fallback for whatever note text
 * doesn't get a confident match either way.
 *
 * (Compare [CategoryCleanup], which handles the old names that *do* have one unambiguous real
 * equivalent, and migrates+deletes them outright instead of just widening the AI backfill.)
 */
object LegacyCategories {

    private data class Bucket(val mainCategory: String, val name: String, val type: TransactionType)

    private val AMBIGUOUS_BUCKETS = setOf(
        Bucket("Home", "Utilities", TransactionType.EXPENSE),
        Bucket("Transportation", "Transportation", TransactionType.EXPENSE),
        Bucket("Leisure", "Dining", TransactionType.EXPENSE),
        Bucket("Leisure", "Entertainment", TransactionType.EXPENSE)
    )

    fun isAmbiguousBucket(category: Category): Boolean {
        return AMBIGUOUS_BUCKETS.any {
            it.mainCategory == category.mainCategory && it.name == category.name && it.type == category.type
        }
    }
}
