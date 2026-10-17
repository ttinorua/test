package com.financetracker.app

import com.financetracker.app.util.MerchantLogos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantLogosTest {

    @Test
    fun knownDomain_matchesCommonMerchantsInBankText() {
        assertEquals("netto.dk", MerchantLogos.knownDomain("Dankort-køb NETTO 1234 Aarhus C"))
        assertEquals("rema1000.dk", MerchantLogos.knownDomain("REMA 1000 Vejle"))
        assertEquals("7-eleven.dk", MerchantLogos.knownDomain("7-Eleven Kbh H"))
        assertEquals("mcdonalds.com", MerchantLogos.knownDomain("McDonald's Odense"))
        assertEquals("hm.com", MerchantLogos.knownDomain("H&M 512"))
    }

    @Test
    fun knownDomain_prefersMerchantOverMobilePay() {
        assertEquals("netto.dk", MerchantLogos.knownDomain("MobilePay Netto"))
        assertEquals("mobilepay.dk", MerchantLogos.knownDomain("MobilePay Jens Hansen"))
    }

    @Test
    fun knownDomain_needsWholeWords() {
        assertNull(MerchantLogos.knownDomain("Spareplan overførsel")) // not "spar"
        assertNull(MerchantLogos.knownDomain("Café Rosa"))
    }

    @Test
    fun merchantKey_dropsDigitsAndPaymentNoise() {
        assertEquals("café rosa aarhus", MerchantLogos.merchantKey("Dankort-køb Café Rosa 0412 Aarhus C"))
        assertEquals(
            MerchantLogos.merchantKey("Visa Café Rosa Aarhus"),
            MerchantLogos.merchantKey("Dankort-køb CAFÉ ROSA 12.10 Aarhus")
        )
        assertNull(MerchantLogos.merchantKey("12.10.2026 DK"))
    }

    @Test
    fun sanitizeDomain_acceptsOnlyBareDomains() {
        assertEquals("netto.dk", MerchantLogos.sanitizeDomain("https://www.Netto.dk/"))
        assertEquals("cafe-rosa.dk", MerchantLogos.sanitizeDomain(" cafe-rosa.dk "))
        assertNull(MerchantLogos.sanitizeDomain("null"))
        assertNull(MerchantLogos.sanitizeDomain("not a domain"))
        assertNull(MerchantLogos.sanitizeDomain("evil.com\" onerror"))
        assertNull(MerchantLogos.sanitizeDomain(null))
    }

    @Test
    fun parseDomainReply_mapsEveryKeyAndMarksMissesEmpty() {
        val reply = "```json\n{\"café rosa\": \"caferosa.dk\", \"jens hansen\": null}\n```"
        val result = MerchantLogos.parseDomainReply(reply, listOf("café rosa", "jens hansen", "other"))
        assertEquals(mapOf("café rosa" to "caferosa.dk", "jens hansen" to "", "other" to ""), result)
        assertNull(MerchantLogos.parseDomainReply("Sorry, I can't help", listOf("x")))
    }
}
