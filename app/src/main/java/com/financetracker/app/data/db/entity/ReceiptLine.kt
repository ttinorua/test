package com.financetracker.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single line on a [Receipt] — one product (or a deposit/discount line).
 *
 * Deleting the parent receipt cascades its lines away. [categoryId] is the result of running the
 * line through the app's existing AI categorisation chain and is nullable (not yet categorised, or
 * the category was later deleted → SET_NULL) so a receipt's lines are always usable even before
 * categorisation has run. [deposit] holds Danish pant and [discount] the member/offer reduction;
 * both are kept as their own fields rather than folded into [lineTotal] so the breakdown survives.
 */
@Entity(
    tableName = "receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("receiptId"), Index("categoryId")]
)
data class ReceiptLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val receiptId: Long,
    val name: String,
    val quantity: Double = 1.0,
    /** Price for one unit before [discount]. */
    val unitPrice: Double = 0.0,
    /** What this line actually contributed to the receipt total, after discount, incl. deposit. */
    val lineTotal: Double = 0.0,
    /** Member/offer reduction on this line, as a positive amount. */
    val discount: Double = 0.0,
    /** Danish deposit (pant) on this line, as a positive amount. */
    val deposit: Double = 0.0,
    val categoryId: Long? = null
)
