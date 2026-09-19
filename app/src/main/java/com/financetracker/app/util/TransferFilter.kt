package com.financetracker.app.util

import com.financetracker.app.data.db.entity.TransactionType

private const val TRANSFER_MAIN_CATEGORY = "other"
private const val TRANSFER_CATEGORY_NAME = "other (transfer)"

/** Matches the bank export's own category for a transfer between the user's own accounts (e.g.
 * moving money into savings) — not real spending, just money changing accounts. */
fun isTransferCategory(mainCategoryName: String?, categoryName: String?): Boolean {
    return mainCategoryName?.trim()?.lowercase() == TRANSFER_MAIN_CATEGORY &&
        categoryName?.trim()?.lowercase() == TRANSFER_CATEGORY_NAME
}

/**
 * Whether a transaction should count toward spending/expense totals, given the user's
 * exclude-transfers preference (Settings > "Exclude transfers from spending"). An "Other
 * (Transfer)" expense is excluded when [enabled]; everything else always counts. The
 * transaction's own stored data never changes — this only affects which totals it's summed
 * into, the same way [effectiveReportingDate] only shifts which period a salary counts toward.
 */
fun countsTowardSpending(
    type: TransactionType,
    mainCategoryName: String?,
    categoryName: String?,
    enabled: Boolean
): Boolean {
    if (!enabled || type != TransactionType.EXPENSE) return true
    return !isTransferCategory(mainCategoryName, categoryName)
}
