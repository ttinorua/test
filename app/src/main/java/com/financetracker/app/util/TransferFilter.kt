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
 * When [transfersInAreIncome] (see [transfersInCountAsIncome]), money transferred *in* from
 * another of the user's accounts is income — a budget or personal account is funded entirely by
 * transfers, and would otherwise always show ~0 income. Outgoing transfers still stay out of
 * spending.
 *
 * The transaction's own stored data never changes — this only affects which totals it's summed
 * into, the same way [effectiveReportingDate] only shifts which period a salary counts toward.
 */
fun countsTowardTotals(
    type: TransactionType,
    mainCategoryName: String?,
    categoryName: String?,
    excludeTransfers: Boolean,
    transfersInAreIncome: Boolean
): Boolean {
    if (!excludeTransfers || !isTransferCategory(mainCategoryName, categoryName)) return true
    return transfersInAreIncome && type == TransactionType.INCOME
}

/** Transfers in count as income only when viewing a single account other than the main one —
 * the main account is where real income lands, so money moved back into it (e.g. from savings)
 * isn't income, while every other account is funded by transfers. */
fun transfersInCountAsIncome(selectedAccountId: Long?, mainAccountId: Long?): Boolean =
    selectedAccountId != null && selectedAccountId != mainAccountId
