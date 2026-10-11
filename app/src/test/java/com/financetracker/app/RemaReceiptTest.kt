package com.financetracker.app

import com.financetracker.app.data.stores.Rema1000
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The REMA 1000 (Scan Selv) response layout isn't confirmed, so these check the flexible reading
 * on layouts shaped like the fields the Scan Selv app stores receipts under. */
class RemaReceiptTest {

    @Test
    fun `a list of receipts with items and price parts is read`() {
        val body = """
            {"receipts": [
              {"id": "991", "displayId": "R-991", "paymentDate": "2026-10-09T16:42:10Z",
               "totalPrice": 47.50, "totalDiscount": 5.00, "totalVat": 9.50,
               "store": {"storeNumber": 123, "name": "Taastrup"},
               "items": [
                 {"id": 1, "shelfText1": "SKRABEÆG", "shelfText2": "10 STK", "prices": [{"price": 30.00, "quantity": 1, "discount": 5.00, "deposit": 0}]},
                 {"id": 2, "shelfText1": "COLA 1,5L", "prices": [{"price": 20.00, "quantity": 2, "discount": 0, "deposit": 2.50}]}
               ]}
            ]}
        """.trimIndent()
        val receipts = Rema1000.parseReceipts(body)
        assertEquals(1, receipts.size)
        val r = receipts[0].receipt
        assertEquals("rema:991", r.receiptNumber)
        assertEquals("REMA 1000 Taastrup", r.storeName)
        assertEquals(47.50, r.total, 0.001)
        assertEquals(5.00, r.discount, 0.001)
        assertEquals(2, r.items.size)
        assertEquals("SKRABEÆG 10 STK", r.items[0].name)
        // 30 - 5 discount + 20 + 2.50 deposit = 47.50: the reading with deposit adds up.
        assertEquals(25.00, r.items[0].amount, 0.001)
        assertEquals(22.50, r.items[1].amount, 0.001)
        assertEquals(2.0, r.items[1].quantity, 0.001)
    }

    @Test
    fun `a bare array with epoch dates and flat prices is read`() {
        val body = """[{"id": 5, "paymentDate": 1760028130000, "totalPrice": 12.0,
            "items": [{"shelfText1": "BANANER", "price": 12.0, "quantity": 1, "discount": 0}]}]"""
        val r = Rema1000.parseReceipts(body).single().receipt
        assertEquals(1760028130000L, r.purchasedAt)
        assertEquals(12.0, r.items.single().amount, 0.001)
    }

    @Test
    fun `no receipts is an empty list, not an error`() {
        assertTrue(Rema1000.parseReceipts("""{"data": {"receipts": []}}""").isEmpty())
        assertTrue(Rema1000.parseReceipts("[]").isEmpty())
    }

    @Test
    fun `an unreadable layout names its fields, never their values`() {
        try {
            Rema1000.parseReceipts("""{"receipts": [{"uuid": "secret-123", "when": "x"}]}""")
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("uuid"))
            assertTrue(!e.message!!.contains("secret-123"))
        }
    }

    @Test
    fun `the receipt request forms tried carry the since date`() {
        val since = 1760028130000L // 2025-10-09T16:42:10Z
        val forms = Rema1000.queries(since)
        assertEquals("", forms[0])
        assertTrue(forms.contains("?sinceDate=2025-10-09T16%3A42%3A10Z"))
        assertTrue(forms.contains("?sinceDate=2025-10-09"))
        assertTrue(forms.contains("?since=1760028130000"))
    }
}
