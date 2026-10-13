package com.financetracker.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.financetracker.app.data.db.entity.Receipt
import com.financetracker.app.data.db.entity.ReceiptLine
import com.financetracker.app.data.db.entity.ReceiptWithLines
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<ReceiptLine>): List<Long>

    @Update
    suspend fun update(receipt: Receipt)

    /** Receipt ids already stored for a connector, so a sync can skip re-importing them. */
    @Query("SELECT externalId FROM receipts WHERE source = :source")
    suspend fun existingExternalIds(source: String): List<String>

    @Query("SELECT * FROM receipts WHERE externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(externalId: String): Receipt?

    @Query("UPDATE receipts SET linkedTransactionId = :transactionId WHERE id = :receiptId")
    suspend fun linkToTransaction(receiptId: Long, transactionId: Long?)

    @Query("UPDATE receipt_lines SET categoryId = :categoryId WHERE id = :lineId")
    suspend fun updateLineCategory(lineId: Long, categoryId: Long?)

    @Query("SELECT * FROM receipts ORDER BY purchasedAt DESC")
    fun observeReceipts(): Flow<List<Receipt>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getWithLines(receiptId: Long): ReceiptWithLines?

    @Transaction
    @Query("SELECT * FROM receipts WHERE linkedTransactionId = :transactionId LIMIT 1")
    fun observeForTransaction(transactionId: Long): Flow<ReceiptWithLines?>

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()
}
