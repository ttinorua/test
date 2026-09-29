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
 * Whether a transaction should count toward income/expense totals, given the user's
 * exclude-transfers preference (Settings > "Exclude transfers from totals"). With
 * [excludeTransfers] on, an "Other (Transfer)" transaction is left out in both directions across
 * "All accounts" — money moved into savings isn't spending, and money moved back isn't income.
 *
 * Viewing a [singleAccount], though, money transferred *in* from another of the user's accounts
 * is that account's income — a budget or personal account is funded entirely by transfers, and
 * would otherwise always show ~0 income. Outgoing transfers still stay out of spending.
 *
 * The transaction's own stored data never changes — this only affects which totals it's summed
 * into, the same way [effectiveReportingDate] only shifts which period a salary counts toward.
 */
fun countsTowardTotals(
    type: TransactionType,
    mainCategoryName: String?,
    categoryName: String?,
    excludeTransfers: Boolean,
    singleAccount: Boolean
): Boolean {
    if (!excludeTransfers || !isTransferCategory(mainCategoryName, categoryName)) return true
    return singleAccount && type == TransactionType.INCOME
}
