package ru.privatenull.pnlibrary.api.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandBuilderTest {
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
        override val id: String = "sender-id"
        override val name: String = "sender"
        override val isConsole: Boolean = false
        override fun hasPermission(permission: String): Boolean = true
        override fun send(message: Component) = Unit
    }
}
