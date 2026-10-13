package com.financetracker.app.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A [Receipt] together with all its [ReceiptLine]s, for the receipt-detail view. */
data class ReceiptWithLines(
    @Embedded val receipt: Receipt,
    @Relation(parentColumn = "id", entityColumn = "receiptId")
    val lines: List<ReceiptLine>
)
