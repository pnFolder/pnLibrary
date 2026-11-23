package org.pnfolder.pnlibrary.api.commands.definition

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import java.util.Locale

class CommandBuilderTest {
    @Test
    fun `command normalization is independent of system locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("info", command("INFO") {}.name)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `builder normalizes command metadata and invokes synchronous handlers`() {
        var executedWith: List<String>? = null
        val definition = command("  Hello ") {
            aliases("HI", "hello-there")
            permission("example.hello")
            consoleBypassesPermission()
            executes { executedWith = it.arguments }
            suggests { listOf("world") }
        }

        assertEquals("hello", definition.name)
        assertEquals(setOf("hi", "hello-there"), definition.aliases)
        assertEquals("example.hello", definition.permission)
        assertTrue(definition.consoleBypassesPermission)

        val context = CommandContext(TestSender(), listOf("one"), "HI", "o")
        definition.execution.execute(context).toCompletableFuture().join()
        assertEquals(listOf("one"), executedWith)
        assertEquals(listOf("world"), definition.suggestions.suggest(context).toCompletableFuture().join())
    }

    @Test
    fun `blank and malformed names are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { command(" ") {} }
        assertThrows(IllegalArgumentException::class.java) { command("bad name") {} }
        assertThrows(IllegalArgumentException::class.java) {
            command("good") { aliases("bad alias") }
        }
    }

    @Test
    fun `primary name cannot also be an alias`() {
        assertThrows(IllegalArgumentException::class.java) {
            command("hello") { aliases("HELLO") }
        }
    }

    @Test
    fun `built aliases are defensive immutable copies`() {
        val source = arrayOf("first")
        val definition = command("hello") { aliases(*source) }

        source[0] = "changed"

        assertEquals(setOf("first"), definition.aliases)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (definition.aliases as MutableSet<String>).add("second")
        }
    }

    private class TestSender : CommandSender {
        override fun pointers() = org.pnfolder.pnlibrary.api.sender.senderPointers("sender", java.util.UUID.randomUUID()) { permission -> true }
        override fun send(message: Component) = Unit
    }
}
