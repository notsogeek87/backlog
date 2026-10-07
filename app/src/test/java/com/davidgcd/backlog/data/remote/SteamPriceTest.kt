package com.davidgcd.backlog.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamPriceTest {
    private val onSale = """{"1145360":{"success":true,"data":{"price_overview":{"currency":"EUR","initial":2499,"final":1249,"discount_percent":50,"final_formatted":"12,49€"}}}}"""

    @Test
    fun `reads the current price and the discount`() {
        val price = SteamPriceParser.parse(onSale, 1145360)!!
        assertEquals(50, price.discountPercent)
        assertEquals(1249L, price.finalCents)
        assertEquals(2499L, price.initialCents)
        assertEquals("12,49€", price.finalFormatted)
    }

    @Test
    fun `a free game answers with an empty array and has no price`() {
        assertNull(SteamPriceParser.parse("""{"570":{"success":true,"data":[]}}""", 570))
    }

    @Test
    fun `an unknown app, a failure and garbage are all no price`() {
        assertNull(SteamPriceParser.parse("""{"1":{"success":false}}""", 1))
        assertNull(SteamPriceParser.parse(onSale, 999))
        assertNull(SteamPriceParser.parse("not json", 1))
    }

    @Test
    fun `a deal is announced once per price and only from twenty percent`() {
        val deal = SteamPrice(discountPercent = 40, finalCents = 1499, initialCents = 2499, finalFormatted = null)
        assertTrue(DealRules.shouldNotify(deal, null))
        assertFalse("same price already announced", DealRules.shouldNotify(deal, 1499L))
        assertTrue("a deeper cut is announced again", DealRules.shouldNotify(deal.copy(finalCents = 999), 1499L))
        assertFalse("too small a discount", DealRules.shouldNotify(deal.copy(discountPercent = 15), null))
    }
}
