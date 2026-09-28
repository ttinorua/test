package com.financetracker.app.data.ai

import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionType

/**
 * A free, instant first pass before ever asking Claude: a hand-picked list of common merchant
 * name patterns (mainly Danish retailers/services, since this app syncs a Danish bank, plus
 * common international subscriptions), matched by substring against the transaction note. This
 * never invents a category — a pattern match only counts if the user's own category list
 * actually contains one of its candidate (mainCategory, name) pairs (case-insensitive), the same
 * rule the AI matcher itself follows. On a real first import (a big share of transactions are
 * usually the same handful of recurring merchants — groceries, fuel, subscriptions, salary), this
 * resolves a meaningful chunk of merchants without a network call at all, leaving Claude
 * (batched, see [CategorySuggester.suggestBatch]) to only handle whatever's left unrecognized.
 *
 * Candidates are ordered with [com.financetracker.app.data.db.DefaultCategories]' real Enable
 * Banking / Sydbank category names first (e.g. "Transportation"/"Fuel", "Home"/"Electricity") —
 * extracted from a real account's own categorized history — since those are what an install that
 * has run [com.financetracker.app.data.db.DefaultCategories.ensure] will actually have; older or
 * hand-named equivalents are kept as fallbacks for installs that predate that starter set.
 */
object LocalCategoryMatcher {

    private data class Rule(
        val keywords: List<String>,
        val type: TransactionType,
        /** Tried in order; the first one that exists in the user's real category list wins. */
        val candidates: List<Pair<String, String>>
    )

    private val rules = listOf(
        // Groceries / supermarkets (Danish chains + common discounters)
        Rule(
            listOf(
                "NETTO", "REMA 1000", "REMA1000", "FOETEX", "FØTEX", "BILKA", "FAKTA", "LIDL",
                "IRMA", "SUPERBRUGSEN", "DAGLI'BRUGSEN", "DAGLIBRUGSEN", "ALDI", "SPAR", "MENY",
                "KVICKLY", "LOEVBJERG", "LØVBJERG"
            ),
            TransactionType.EXPENSE,
            listOf("Food" to "Groceries", "Groceries" to "Food")
        ),
        // Bakery / butcher / specialty food shops
        Rule(
            listOf("BAGER", "BAGERI", "SLAGTER", "VINHANDEL", "OST ", "FISKEHANDEL"),
            TransactionType.EXPENSE,
            listOf("Food" to "Bakery, butcher, wine shop etc.", "Food" to "Groceries")
        ),
        // Fuel / gas stations
        Rule(
            listOf("SHELL", "CIRCLE K", "OK BENZIN", "OK PLUS", " OK ", "Q8", "UNO-X", "UNOX", "GO' ON", "F24"),
            TransactionType.EXPENSE,
            listOf("Transportation" to "Fuel", "Transport" to "Fuel", "Fuel" to "Transport")
        ),
        // Parking
        Rule(
            listOf("APCOA", "EASYPARK", "PARKERING", "PARKING"),
            TransactionType.EXPENSE,
            listOf("Transportation" to "Parking")
        ),
        // Public transport / taxis
        Rule(
            listOf(
                "DSB", "REJSEKORT", "REJSEKORT.DK", "MOVIA", "METRO", "FLIXBUS", "MIDTTRAFIK",
                "NORDJYSKE JB", "UBER", "TAXA", "TAXI"
            ),
            TransactionType.EXPENSE,
            listOf(
                "Transportation" to "Taxis and public transportation",
                "Transport" to "Public Transport",
                "Transportation" to "Transportation"
            )
        ),
        // Bridge tolls / ferry
        Rule(
            listOf("STOREBAELT", "STOREBÆLT", "OERESUNDSBRO", "ØRESUNDSBRO", "FAERGE", "FÆRGE"),
            TransactionType.EXPENSE,
            listOf("Transportation" to "Bridge tolls and ferry ticket")
        ),
        // Streaming / phone / internet / TV subscriptions
        Rule(
            listOf(
                "NETFLIX", "HBO", "MAX.COM", "DISNEY+", "DISNEY PLUS", "VIAPLAY", "SPOTIFY",
                "YOUTUBE PREMIUM", "TV 2 PLAY", "TV2 PLAY", "TDC", "YOUSEE", "TELENOR", "TELIA",
                "3 DANMARK", "CBB MOBIL", "OISTER", "LEBARA", "LYCAMOBILE"
            ),
            TransactionType.EXPENSE,
            listOf(
                "Media" to "Phone, internet, streaming and TV",
                "Entertainment" to "Streaming",
                "Leisure" to "Entertainment"
            )
        ),
        // Digital purchases: apps, games, films, music, software
        Rule(
            listOf("APPLE.COM/BILL", "GOOGLE PLAY", "STEAM", "PLAYSTATION", "XBOX", "NINTENDO", "AMAZON PRIME"),
            TransactionType.EXPENSE,
            listOf("Media" to "Films, music, apps and software", "Entertainment" to "Streaming")
        ),
        // Fast food / delivery
        Rule(
            listOf("MCDONALD", "BURGER KING", "SUNSET BOULEVARD", "WOLT", "JUST EAT", "FOODORA"),
            TransactionType.EXPENSE,
            listOf("Food" to "Take away and fast food", "Food" to "Restaurants")
        ),
        // Café / restaurant / bar
        Rule(
            listOf("STARBUCKS", "ESPRESSO HOUSE", "RESTAURANT", "PIZZA", "CAFE ", "CAFÉ ", "BAR "),
            TransactionType.EXPENSE,
            listOf("Leisure" to "Café, restaurant and bar", "Food" to "Restaurants", "Leisure" to "Dining")
        ),
        // Pharmacy / doctor / dentist
        Rule(
            listOf("MATAS", "APOTEK", "TANDLAEGE", "TANDLÆGE", "LAEGE", "LÆGE"),
            TransactionType.EXPENSE,
            listOf(
                "Clothing and pers. care prod." to "Dentist, doctor and medication",
                "Health" to "Pharmacy",
                "Healthcare" to "Clothing and pers. care prod."
            )
        ),
        // Hair / skin care
        Rule(
            listOf("FRISOER", "FRISØR", "HAIRDRESSER", "BARBER", "SALON"),
            TransactionType.EXPENSE,
            listOf("Clothing and pers. care prod." to "Hair and skin care")
        ),
        // Electricity
        Rule(
            listOf("OERSTED", "ØRSTED", "NORLYS", "SEAS-NVE", "ANDEL ENERGI", "N1", "VESTFORSYNING", "HOFOR"),
            TransactionType.EXPENSE,
            listOf("Home" to "Electricity", "Home" to "Utilities", "Utilities" to "Home")
        ),
        // Furniture / home goods / hardware / DIY
        Rule(
            listOf(
                "IKEA", "SILVAN", "LUX-CASE", "JYSK", "BAUHAUS", "PLANTORAMA", "BILTEMA",
                "ILVA", "BOLIA", "IDEMOEBLER", "IDÉMØBLER"
            ),
            TransactionType.EXPENSE,
            listOf("Home" to "Furniture and home accessories", "Shopping" to "Shopping")
        ),
        // Electronics
        Rule(
            listOf("ELGIGANTEN", "POWER ", "COMPUTERSALG", "PROSHOP", "AVXPERTEN"),
            TransactionType.EXPENSE,
            listOf("Leisure" to "Electronics and gadgets", "Shopping" to "Shopping")
        ),
        // General online marketplaces / clothing
        Rule(
            listOf("AMAZON", "EBAY", "ALIEXPRESS", "ZALANDO", "H&M", "ASOS"),
            TransactionType.EXPENSE,
            listOf(
                "Clothing and pers. care prod." to "Clothing, shoes and accessories",
                "Shopping" to "Clothing",
                "Shopping" to "Shopping"
            )
        ),
        // Membership / union / unemployment insurance fees
        Rule(
            listOf("A-KASSE", "AKASSE", "FAGFORENING"),
            TransactionType.EXPENSE,
            listOf("Insurance" to "Union and unemployment insurance")
        ),
        // General insurance
        Rule(
            listOf("FORSIKRING", "TRYG", "TOPDANMARK", "IF SKADEFORSIKRING", "ALKA", "CODAN"),
            TransactionType.EXPENSE,
            listOf("Insurance" to "Union and unemployment insurance", "Home" to "Utilities")
        ),
        // Transfers to another bank/account — unambiguous only for named fintech/neobank
        // destinations, never a generic "overførsel/transfer" keyword, which is just as likely
        // to be a specific, already-categorizable payment (e.g. "Overførsel: Husleje" = rent).
        Rule(
            listOf("LUNAR BANK", "REVOLUT", "BANK NORWEGIAN", "SAVING ACCOUNT", "SAVINGS ACCOUNT"),
            TransactionType.EXPENSE,
            listOf("Other" to "Other (Transfer)", "Transfers" to "Transfers")
        ),
        // Rent
        Rule(
            listOf("HUSLEJE", "BOLIGSELSKAB"),
            TransactionType.EXPENSE,
            listOf("Home" to "Rent")
        ),
        // Salary / income
        Rule(
            listOf("LOEN", "LØN", "SALARY", "PAYROLL"),
            TransactionType.INCOME,
            listOf("Income" to "Pay, benefits and pension", "Income" to "Salary")
        )
    )

    /** Returns a real [Category] from [categories] for a local pattern match, or null if nothing
     * matched (or matched but the user has no corresponding category) — a null here means "ask
     * the AI", never "categorize as nothing". */
    fun suggest(note: String, categories: List<Category>): Category? {
        if (note.isBlank() || categories.isEmpty()) return null
        val upper = note.uppercase()
        for (rule in rules) {
            if (rule.keywords.none { upper.contains(it) }) continue
            for ((main, sub) in rule.candidates) {
                val match = categories.firstOrNull {
                    it.type == rule.type &&
                        it.mainCategory.equals(main, ignoreCase = true) &&
                        it.name.equals(sub, ignoreCase = true)
                }
                if (match != null) return match
            }
        }
        return null
    }
}
