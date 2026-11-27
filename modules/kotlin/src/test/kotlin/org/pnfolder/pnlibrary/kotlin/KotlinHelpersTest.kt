package org.pnfolder.pnlibrary.kotlin

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.network.message.MessageCodec
import org.pnfolder.pnlibrary.api.services.ServiceManager

class KotlinHelpersTest {
    private data class Balance(val id: UUID, val coins: Int)

    private val balance = messageCodec<Balance>(
        encode = { b -> writeUuid(b.id).writeVarInt(b.coins) },
        decode = { Balance(readUuid(), readVarInt()) },
    )

    @Test
    fun `a codec built from lambdas reads back what it wrote`() {
        val value = Balance(UUID.randomUUID(), 250)
        assertEquals(value, balance.fromBytes(balance.toBytes(value)))
    }

    @Test
    fun `it works with listOf and compressed like any codec`() {
        val values = List(100) { Balance(UUID.randomUUID(), it) }
        val codec = MessageCodec.compressed(MessageCodec.listOf(balance))
        assertEquals(values, codec.fromBytes(codec.toBytes(values)))
    }

    @Test
    fun `reified service calls pass the class`() {
        val services = mock(ServiceManager::class.java)
        `when`(services.get(String::class.java)).thenReturn("shop")
        `when`(services.require(String::class.java)).thenReturn("shop")
        `when`(services.all(String::class.java)).thenReturn(listOf("shop", "bank"))

        assertEquals("shop", services.get<String>())
        assertEquals("shop", services.require<String>())
        assertEquals(listOf("shop", "bank"), services.all<String>())
        assertNull(services.get<Int>())

        services.register("shop", 5)
        services.unregister<String>()
        verify(services).register(String::class.java, "shop", 5)
        verify(services).unregister(String::class.java)
    }
}
