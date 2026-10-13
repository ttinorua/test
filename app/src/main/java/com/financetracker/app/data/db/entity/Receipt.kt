package com.financetracker.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One itemised store receipt pulled from a loyalty connector (Coop/Lobyco today).
 *
 * A receipt exists independently of the bank transaction that paid for it: the Coop sync saves
 * receipts as it finds them, and matching to a [Transaction] happens separately and may stay
 * unresolved (e.g. the bank hasn't synced that payment yet), so [linkedTransactionId] is nullable
 * and clears to null if the matched transaction is later deleted rather than taking the receipt
 * with it. [externalId] is the connector's own receipt id and is unique so re-syncing never
 * duplicates a receipt already stored.
 */
@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["linkedTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("externalId", unique = true),
        Index("linkedTransactionId"),
        Index("purchasedAt")
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** The connector's own receipt id (Lobyco `receiptId`); unique, used to skip re-imports. */
    val externalId: String,
    /** Which connector produced this receipt, e.g. "coop". */
    val source: String,
    val storeName: String,
    /** Epoch millis of the purchase instant as reported by the connector. */
    val purchasedAt: Long,
    val total: Double,
    val currencyCode: String = "DKK",
    /** The bank [Transaction] this receipt was matched to, or null until matched. */
    val linkedTransactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
