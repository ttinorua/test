package com.financetracker.app.util

private const val TRANSFER_MAIN_CATEGORY = "other"
private const val TRANSFER_CATEGORY_NAME = "other (transfer)"

/** Matches the bank export's own category for a transfer between the user's own accounts (e.g.
 * moving money into savings) — not real spending, just money changing accounts. */
fun isTransferCategory(mainCategoryName: String?, categoryName: String?): Boolean {
    return mainCategoryName?.trim()?.lowercase() == TRANSFER_MAIN_CATEGORY &&
        categoryName?.trim()?.lowercase() == TRANSFER_CATEGORY_NAME
}

/**
 * Whether a transaction should count toward income/expense totals, given the user's
 * exclude-transfers preference (Settings > "Exclude transfers from totals"). An "Other
 * (Transfer)" transaction is excluded when [enabled] in both directions — money moved into
 * savings isn't spending, and money moved back isn't income. The transaction's own stored data
 * never changes — this only affects which totals it's summed into, the same way
 * [effectiveReportingDate] only shifts which period a salary counts toward.
 */
fun countsTowardTotals(
    mainCategoryName: String?,
    categoryName: String?,
    enabled: Boolean
): Boolean {
    if (!enabled) return true
    return !isTransferCategory(mainCategoryName, categoryName)
}
