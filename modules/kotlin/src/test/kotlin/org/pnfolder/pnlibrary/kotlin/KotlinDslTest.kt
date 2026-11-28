package org.pnfolder.pnlibrary.kotlin

import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.cooldowns.Cooldown
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel
import org.pnfolder.pnlibrary.api.network.message.MessageReader
import org.pnfolder.pnlibrary.api.network.message.MessageWriter

class KotlinDslTest {
    @Test
    fun `a command is built with the builder as this`() {
        val definition = command("shop") {
            permission("shop.use")
            sub("buy") {
                arg("item", ArgumentType.string()) { sub("now") { } }
            }
        }
        assertEquals("shop", definition.name)
        val words = definition.root.children.map { it.name }
        assertEquals(listOf("buy"), words)
        assertEquals(listOf("item"), definition.root.children[0].children.map { it.name })
    }

    @Test
    fun `a typed message is written with the writer as this`() {
        val channel = mock(NetworkChannel::class.java)
        val server = mock(NetworkServer::class.java)
        `when`(channel.send(eq(server), eq("balance"), any())).thenCallRealMethod()
        channel.sendMessage(server, "balance") { writeInt(250) }
        verify(channel).send(eq(server), eq("balance"), any())

        val written = MessageWriter.encode("balance") { it.writeInt(250) }
        val reader = MessageReader.of(written)!!
        assertEquals("balance", reader.type)
        assertEquals(250, reader.readInt())
    }

    @Test
    fun `the server side sends the same way`() {
        val channel = mock(ServerChannel::class.java)
        `when`(channel.send(eq("hello"), any())).thenReturn(CompletableFuture.completedFuture(true))
        assertEquals(true, channel.sendMessage("hello") { writeText("hi") }.toCompletableFuture().get())
    }

    @Test
    fun `a cooldown takes a kotlin duration`() {
        val builder = mock(Cooldown.Builder::class.java)
        `when`(builder.duration(any(java.time.Duration::class.java))).thenReturn(builder)
        builder.duration(30.minutes)
        verify(builder).duration(java.time.Duration.ofMinutes(30))

        val service = mock(CooldownService::class.java)
        val kit = mock(Cooldown::class.java)
        `when`(service.cooldown(eq("kit"), any<java.util.function.Consumer<Cooldown.Builder>>())).thenReturn(kit)
        assertEquals(kit, service.define("kit") { })
    }
}
