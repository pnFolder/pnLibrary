package org.pnfolder.pnlibrary.api.audiences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeliveryTest {
    private val failed = Delivery.Failed(IllegalStateException("boom"))

    @Test
    fun `combined delivery succeeds only when every receiver got it`() {
        assertEquals(Delivery.Delivered, Delivery.combine(listOf(Delivery.Delivered, Delivery.Delivered)))
        assertEquals(Delivery.Unsupported, Delivery.combine(listOf(Delivery.Delivered, Delivery.Unsupported)))
    }

    @Test
    fun `a failure outranks unsupported and no receivers is unsupported`() {
        assertEquals(failed, Delivery.combine(listOf(Delivery.Unsupported, failed, Delivery.Delivered)))
        assertEquals(Delivery.Unsupported, Delivery.combine(emptyList()))
    }

    @Test
    fun `only delivered counts as delivered`() {
        assertTrue(Delivery.of(true).isDelivered)
        assertFalse(Delivery.of(false).isDelivered)
        assertFalse(failed.isDelivered)
    }
}
