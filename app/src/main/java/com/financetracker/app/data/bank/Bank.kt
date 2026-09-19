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
 * taxonomy. This exists as its own type (rather than folding a bank's constants and category
 * list straight into [com.financetracker.app.data.enablebanking.EnableBankingService]) so
 * adding another bank is just adding a [Bank] entry to [SupportedBanks.ALL], not a redesign: the
 * Enable Banking auth call already takes [aspspName]/[aspspCountry] from here rather than a
 * hardcoded constant, and [BankCategories.ensure] already seeds whichever bank is selected.
 *
 * [aspspName] is whatever Enable Banking's own `GET /aspsps` registry calls this bank *right
 * now* — verified live against that endpoint for each entry below, not guessed from the bank's
 * current public branding, since those can disagree: SJF Bank rebranded from "Sparekassen
 * Sjælland-Fyn" in 2025, but Enable Banking's registry still lists it under the old name as of
 * this writing. [displayName] is what the app shows the user, and is free to use the current
 * name — only [aspspName] has to match Enable Banking's registry exactly, since it's sent
 * verbatim in the `/auth` request's `aspsp.name` field to select which bank's consent flow to
 * start. If a bank stops resolving, re-check its current registry entry rather than assuming
 * the branding name is still right.
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
        defaultCategories = BankdataDefaultCategories.ALL
    )

    /** Enable Banking's registry still lists this ASPSP under its pre-2025-rebrand name. */
    val SJF_BANK = Bank(
        id = "sjf_bank",
        displayName = "SJF Bank",
        aspspName = "Sparekassen Sjælland-Fyn",
        aspspCountry = "DK",
        defaultCategories = BankdataDefaultCategories.ALL
    )

    /** Every bank this app currently knows how to connect to. Add a new [Bank] here (with its
     * own aspspName/aspspCountry and default category list) to support another one. */
    val ALL = listOf(SYDBANK, SJF_BANK)

    val DEFAULT = SYDBANK

    fun byId(id: String): Bank = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
