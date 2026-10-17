package com.financetracker.app.util

import org.json.JSONObject

/**
 * Turns a transaction's free-text description (e.g. "Dankort-køb NETTO 1234 Aarhus C") into the
 * company's website domain, so the UI can show that company's logo. Transactions carry no
 * merchant ID, so this is all text matching: a built-in list of common merchants first, and a
 * normalised [merchantKey] that the AI lookup can cache its answer under for everything else.
 */
object MerchantLogos {

    /** Keyword → domain. Keywords are matched as whole words against [normalize]d text, in this
     * order, so specific names come before generic ones (MobilePay last: "MobilePay Netto" is Netto). */
    private val KNOWN: List<Pair<String, String>> = listOf(
        "netto" to "netto.dk",
        "rema 1000" to "rema1000.dk",
        "føtex" to "foetex.dk",
        "foetex" to "foetex.dk",
        "bilka" to "bilka.dk",
        "salling" to "salling.dk",
        "lidl" to "lidl.dk",
        "aldi" to "aldi-nord.de",
        "superbrugsen" to "superbrugsen.dk",
        "dagli brugsen" to "dagli-brugsen.dk",
        "kvickly" to "kvickly.dk",
        "irma" to "irma.dk",
        "coop" to "coop.dk",
        "meny" to "meny.dk",
        "spar" to "spar.dk",
        "løvbjerg" to "lovbjerg.dk",
        "7 eleven" to "7-eleven.dk",
        "circle k" to "circlek.dk",
        "q8" to "q8.dk",
        "shell" to "shell.dk",
        "ingo" to "ingo.dk",
        "matas" to "www.matas.dk",
        "jysk" to "jysk.dk",
        "ikea" to "ikea.com",
        "elgiganten" to "elgiganten.dk",
        "bauhaus" to "bauhaus.dk",
        "silvan" to "silvan.dk",
        "harald nyborg" to "harald-nyborg.dk",
        "flying tiger" to "flyingtiger.com",
        "h m" to "hm.com",
        "zalando" to "zalando.dk",
        "amazon" to "amazon.com",
        "apple" to "apple.com",
        "google" to "google.com",
        "microsoft" to "microsoft.com",
        "spotify" to "spotify.com",
        "netflix" to "netflix.com",
        "disney" to "disneyplus.com",
        "hbo" to "max.com",
        "viaplay" to "viaplay.dk",
        "tv 2" to "tv2.dk",
        "yousee" to "yousee.dk",
        "telenor" to "telenor.dk",
        "telia" to "telia.com",
        "dsb" to "dsb.dk",
        "rejsekort" to "rejsekort.dk",
        "mcdonald" to "mcdonalds.com",
        "burger king" to "burgerking.dk",
        "starbucks" to "starbucks.com",
        "wolt" to "wolt.com",
        "just eat" to "just-eat.dk",
        "uber" to "uber.com",
        "airbnb" to "airbnb.com",
        "booking com" to "booking.com",
        "steam" to "steampowered.com",
        "playstation" to "playstation.com",
        "paypal" to "paypal.com",
        "mobilepay" to "mobilepay.dk",
    ).map { (keyword, domain) -> normalize(keyword) to domain }

    /** Payment-method noise banks put in front of the merchant name; dropped from [merchantKey]
     * so "Dankort-køb Café Rosa" and "Visa Café Rosa" share one cached AI answer. */
    private val NOISE = setOf(
        "dankort", "visa", "mastercard", "køb", "kortkøb", "betaling", "nota", "dk", "bs",
        "betalingsservice", "overførsel", "indbetaling", "udbetaling", "fra", "til", "c", "v", "n", "s", "k"
    )

    private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** Lowercase, punctuation → spaces, single-spaced. Digits stay (for "rema 1000", "7-eleven"). */
    fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Domain from the built-in list, or null. */
    fun knownDomain(description: String): String? {
        val padded = " ${normalize(description)} "
        return KNOWN.firstOrNull { (keyword, _) -> " $keyword " in padded }?.second
    }

    /** Stable cache key for a merchant: no digits (store numbers, dates), no payment-method words,
     * at most four words. Null when nothing name-like is left. */
    fun merchantKey(description: String): String? =
        normalize(description).replace(Regex("\\p{N}+"), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in NOISE }
            .take(4)
            .joinToString(" ")
            .takeIf { it.length >= 3 }

    /** Cleans an AI-supplied website into a bare domain ("https://www.Netto.dk/" → "netto.dk"), or
     * null when it isn't one. Keeps a bad answer from ever becoming a request URL. */
    fun sanitizeDomain(raw: String?): String? {
        val host = raw?.trim()?.lowercase()
            ?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")
            ?.substringBefore('/')?.substringBefore('?')
            ?: return null
        return host.takeIf { it.length <= 253 && DOMAIN.matches(it) }
    }

    /** Reads the AI's `{"key": "domain" | null}` reply. Every asked key gets an entry: its domain,
     * or "" meaning "no logo" so it isn't asked again. Null when the reply isn't JSON at all. */
    fun parseDomainReply(text: String, keys: Collection<String>): Map<String, String>? = runCatching {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1))
        keys.associateWith { key -> sanitizeDomain(json.optString(key, "").takeIf { !json.isNull(key) }) ?: "" }
    }.getOrNull()

    /** Small square company icon for [domain]. Unknown domains answer with an error status, so the
     * image loader fails and the UI keeps its fallback. */
    fun logoUrl(domain: String): String = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}
