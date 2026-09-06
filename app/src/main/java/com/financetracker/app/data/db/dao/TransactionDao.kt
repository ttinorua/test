package com.financetracker.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.financetracker.app.data.db.entity.CategorySpend
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query(
        """
        SELECT
            t.id AS id,
            t.amount AS amount,
            t.type AS type,
            t.accountId AS accountId,
            a.name AS accountName,
            t.categoryId AS categoryId,
            c.name AS categoryName,
            c.mainCategory AS mainCategoryName,
            c.colorHex AS categoryColorHex,
            t.date AS date,
            t.note AS note
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun observeAllWithDetails(): Flow<List<TransactionWithDetails>>

    @Query(
        """
        SELECT
            t.id AS id,
            t.amount AS amount,
            t.type AS type,
            t.accountId AS accountId,
            a.name AS accountName,
            t.categoryId AS categoryId,
            c.name AS categoryName,
            c.mainCategory AS mainCategoryName,
            c.colorHex AS categoryColorHex,
            t.date AS date,
            t.note AS note
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        ORDER BY t.date DESC, t.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentWithDetails(limit: Int): Flow<List<TransactionWithDetails>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :from AND :to"
    )
    fun observeIncomeBetween(from: Long, to: Long): Flow<Double>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to"
    )
    fun observeExpenseBetween(from: Long, to: Long): Flow<Double>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) FROM transactions"
    )
    fun observeNetBalance(): Flow<Double>

    @Query(
        """
        SELECT
            COALESCE(c.mainCategory, 'Uncategorized') AS mainCategory,
            c.id AS categoryId,
            COALESCE(c.name, 'Uncategorized') AS categoryName,
            COALESCE(c.colorHex, '#9E9E9E') AS colorHex,
            SUM(t.amount) AS total
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.type = 'EXPENSE' AND t.date BETWEEN :from AND :to
        GROUP BY mainCategory, t.categoryId
        ORDER BY total DESC
        """
    )
    fun observeExpenseByCategoryBetween(from: Long, to: Long): Flow<List<CategorySpend>>
}
