package com.financetracker.app.data.bank

import com.financetracker.app.data.db.entity.TransactionType

data class DefaultCategorySeed(
    val name: String,
    val mainCategory: String,
    val type: TransactionType,
    val colorHex: String
)

/**
 * A bank/ASPSP this app can connect to via Enable Banking, plus that bank's own starter category
 * taxonomy. Only [SupportedBanks.SYDBANK] actually exists today — this exists as its own type
 * (rather than folding Sydbank's constants and category list straight into
 * [com.financetracker.app.data.enablebanking.EnableBankingService]) so adding a second bank
 * later is just adding another [Bank] entry to [SupportedBanks.ALL], not a redesign: the Enable
 * Banking auth call already takes [aspspName]/[aspspCountry] from here rather than a hardcoded
 * constant, and [BankCategories.ensure] already seeds whichever bank is selected.
 */
data class Bank(
    val id: String,
    val displayName: String,
    val aspspName: String,
    val aspspCountry: String,
    val defaultCategories: List<DefaultCategorySeed>
)

object SupportedBanks {
    val SYDBANK = Bank(
        id = "sydbank",
        displayName = "Sydbank",
        aspspName = "Sydbank",
        aspspCountry = "DK",
        defaultCategories = SydbankDefaultCategories.ALL
    )

    /** Every bank this app currently knows how to connect to. Add a new [Bank] here (with its
     * own aspspName/aspspCountry and default category list) to support another one. */
    val ALL = listOf(SYDBANK)

    val DEFAULT = SYDBANK

    fun byId(id: String): Bank = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
