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
                "KVICKLY", "LOEVBJERG", "LØVBJERG", "MENY"
            ),
            TransactionType.EXPENSE,
            listOf("Food" to "Groceries", "Groceries" to "Food")
        ),
        // Fuel / gas stations
        Rule(
            listOf("SHELL", "CIRCLE K", "OK BENZIN", "OK PLUS", " OK ", "Q8", "UNO-X", "UNOX", "GO' ON", "F24"),
            TransactionType.EXPENSE,
            listOf("Transport" to "Fuel", "Transportation" to "Fuel", "Fuel" to "Transport")
        ),
        // Public transport
        Rule(
            listOf("DSB", "REJSEKORT", "REJSEKORT.DK", "MOVIA", "METRO", "FLIXBUS", "MIDTTRAFIK", "NORDJYSKE JB"),
            TransactionType.EXPENSE,
            listOf("Transport" to "Public Transport", "Transportation" to "Public Transport", "Transportation" to "Transportation")
        ),
        // Ride-hailing / parking
        Rule(
            listOf("UBER", "TAXA", "TAXI", "APCOA", "EASYPARK", "PARKERING"),
            TransactionType.EXPENSE,
            listOf("Transport" to "Taxi", "Transportation" to "Transportation")
        ),
        // Streaming / subscriptions
        Rule(
            listOf(
                "NETFLIX", "HBO", "MAX.COM", "DISNEY+", "DISNEY PLUS", "VIAPLAY", "SPOTIFY",
                "YOUTUBE PREMIUM", "TV 2 PLAY", "TV2 PLAY", "APPLE.COM/BILL", "AMAZON PRIME"
            ),
            TransactionType.EXPENSE,
            listOf("Entertainment" to "Streaming", "Leisure" to "Entertainment", "Entertainment" to "Entertainment")
        ),
        // Restaurants / fast food / delivery
        Rule(
            listOf(
                "MCDONALD", "BURGER KING", "SUNSET BOULEVARD", "WOLT", "JUST EAT", "FOODORA",
                "STARBUCKS", "ESPRESSO HOUSE", "RESTAURANT", "PIZZA", "CAFE ", "CAFÉ "
            ),
            TransactionType.EXPENSE,
            listOf("Food" to "Restaurants", "Leisure" to "Dining", "Dining" to "Leisure")
        ),
        // Pharmacy / health / personal care
        Rule(
            listOf("MATAS", "APOTEK", "TANDLAEGE", "TANDLÆGE", "LAEGE", "LÆGE"),
            TransactionType.EXPENSE,
            listOf("Health" to "Pharmacy", "Clothing and pers. care prod." to "Healthcare", "Healthcare" to "Clothing and pers. care prod.")
        ),
        // Telecom / mobile
        Rule(
            listOf("TDC", "YOUSEE", "TELENOR", "TELIA", "3 DANMARK", "CBB MOBIL", "OISTER", "LEBARA", "LYCAMOBILE"),
            TransactionType.EXPENSE,
            listOf("Home" to "Utilities", "Utilities" to "Home")
        ),
        // Electricity / utilities
        Rule(
            listOf("OERSTED", "ØRSTED", "NORLYS", "SEAS-NVE", "ANDEL ENERGI", "N1", "VESTFORSYNING", "HOFOR"),
            TransactionType.EXPENSE,
            listOf("Home" to "Utilities", "Utilities" to "Home")
        ),
        // Online marketplaces / general shopping
        Rule(
            listOf("AMAZON", "EBAY", "ALIEXPRESS", "ZALANDO", "H&M", "ASOS"),
            TransactionType.EXPENSE,
            listOf("Shopping" to "Clothing", "Clothing and pers. care prod." to "Shopping", "Leisure" to "Shopping")
        ),
        // Salary / income
        Rule(
            listOf("LOEN", "LØN", "SALARY", "PAYROLL"),
            TransactionType.INCOME,
            listOf("Income" to "Salary", "Income" to "Pay, benefits and pension")
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
