package org.pnfolder.pnlibrary.api.audience

import org.pnfolder.pnlibrary.api.audience.delivery.DeliveryStatus
import org.pnfolder.pnlibrary.api.audience.delivery.Outcome
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeliveryTest {
    private val cause = IllegalStateException("boom")

    @Test
    fun `the status sums up the receipts`() {
        val ok = Delivery.delivered()
        val no = Delivery.unsupported()
        val bad = Delivery.failed(cause)

        assertEquals(DeliveryStatus.DELIVERED, Delivery.combine(listOf(ok, ok)).status)
        assertEquals(DeliveryStatus.PARTIAL, Delivery.combine(listOf(ok, no)).status)
        assertEquals(DeliveryStatus.PARTIAL, Delivery.combine(listOf(bad, ok)).status)
        assertEquals(DeliveryStatus.FAILED, Delivery.combine(listOf(no, bad)).status)
        assertEquals(DeliveryStatus.UNSUPPORTED, Delivery.combine(listOf(no, no)).status)
        assertEquals(DeliveryStatus.UNSUPPORTED, Delivery.combine(emptyList()).status)
    }

    @Test
    fun `a delivery counts receivers and keeps every failure`() {
        val other = IllegalArgumentException("other")
        val mixed = Delivery.combine(listOf(Delivery.delivered(), Delivery.failed(cause), Delivery.failed(other)))

        assertEquals(1, mixed.delivered)
        assertEquals(3, mixed.receipts.size)
        assertEquals(listOf(cause, other), mixed.failures)
        assertSame(cause, mixed.failure)
        assertNull(Delivery.delivered().failure)
    }

    @Test
    fun `only a full delivery counts as delivered`() {
        assertTrue(Delivery.of(true).isDelivered)
        assertFalse(Delivery.of(false).isDelivered)
        assertFalse(Delivery.failed(cause).isDelivered)
        assertFalse(Delivery.combine(listOf(Delivery.delivered(), Delivery.unsupported())).isDelivered)
    }

    @Test
    fun `orThrow passes a delivery through and throws the failure otherwise`() {
        val ok = Delivery.delivered()

        assertSame(ok, ok.orThrow())
        assertSame(cause, assertThrows(IllegalStateException::class.java) { Delivery.failed(cause).orThrow() }.cause)
        assertThrows(IllegalStateException::class.java) { Delivery.unsupported().orThrow() }
    }

    @Test
    fun `an audience reports whether the operation got through`() {
        val seen = mutableListOf<String>()
        val ok = receiver("ok", seen)
        val broken = receiver("broken", seen, IllegalStateException("lost"))

        assertEquals(DeliveryStatus.DELIVERED, ok.deliver { it.sendMessage(Component.text("hi")) }.status)
        assertEquals("lost", broken.deliver { it.sendMessage(Component.text("hi")) }.failure?.message)
    }

    @Test
    fun `a group asks every receiver and keeps each answer`() {
        val seen = mutableListOf<String>()
        val group = Audience.audience(receiver("first", seen), receiver("broken", seen, cause), receiver("last", seen))

        val sent = group.deliver { it.sendMessage(Component.text("hi")) }

        assertEquals(DeliveryStatus.PARTIAL, sent.status)
        assertEquals(listOf("first", "broken", "last"), seen)
        assertEquals(listOf(Outcome.DELIVERED, Outcome.FAILED, Outcome.DELIVERED), sent.receipts.map { it.outcome })
        assertEquals(listOf("first", "broken", "last"), sent.receipts.map { it.receiver })
    }

    @Test
    fun `a group with nobody in it is unsupported and fatal errors are not caught`() {
        val nobody = Audience.audience(emptyList<Audience>())

        assertEquals(DeliveryStatus.UNSUPPORTED, nobody.deliver { it.sendMessage(Component.text("hi")) }.status)
        assertThrows(OutOfMemoryError::class.java) { receiver("dying", mutableListOf(), OutOfMemoryError()).deliver { it.sendMessage(Component.text("hi")) } }
    }

    private fun receiver(name: String, seen: MutableList<String>, failure: Throwable? = null) = object : Audience {
        override fun pointers() = senderPointers(name) { false }

        override fun sendMessage(message: Component) {
            seen += name
            if (failure != null) throw failure
        }
    }
}
