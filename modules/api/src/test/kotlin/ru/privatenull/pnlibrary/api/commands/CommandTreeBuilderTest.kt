package ru.privatenull.pnlibrary.api.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CommandTreeBuilderTest {
    @Test
    fun `builder creates an immutable mixed-depth tree`() {
        val definition = command("group") {
            argument("group", ArgumentType.string()) {
                suggests { listOf("builders") }
                literal("member") {
                    argument("page", ArgumentType.integer()) {
                        permission("groups.page")
                        availableIf { context -> context.get<String>("group") == "builders" }
                        executes { }
                    }
                }
            }
        }

        val group = definition.root.children.single()
        val member = group.children.single()
        val page = member.children.single()
        assertEquals(CommandNodeKind.ARGUMENT, group.kind)
        assertEquals("member", member.name)
        assertEquals(ArgumentType.integer(), page.argumentType)
        assertEquals("groups.page", page.permission)
        assertTrue(page.isExecutable)
    }

    @Test
    fun `typed context exposes parsed values without changing raw arguments`() {
        val context = CommandContext(
            sender = TestSender,
            arguments = listOf("builders", "12.50"),
            invokedAlias = "group",
            currentInput = "12.50",
            parsedValues = mapOf("group" to "builders", "amount" to BigDecimal("12.50")),
        )

        assertEquals("builders", context.get<String>("group"))
        assertEquals(BigDecimal("12.50"), context.get<BigDecimal>("amount"))
        assertEquals(listOf("builders", "12.50"), context.arguments)
        assertFalse(context.contains("missing"))
        assertThrows(IllegalArgumentException::class.java) { context.get<String>("missing") }
    }

    @Test
    fun `ambiguous sibling arguments and duplicate names are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            command("bad") {
                argument("first", ArgumentType.string()) {}
                argument("second", ArgumentType.integer()) {}
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            command("bad") {
                literal("same") {}
                literal("SAME") {}
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            command("bad") { literal("bad literal") {} }
        }
        assertThrows(IllegalArgumentException::class.java) {
            command("bad") { argument("bad argument", ArgumentType.string()) {} }
        }
    }

    @Test
    fun `built children cannot be mutated through their public list`() {
        val definition = command("safe") { literal("child") {} }

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (definition.root.children as MutableList<CommandNode>).clear()
        }
    }

    private object TestSender : CommandSender {
        override val id = "test"
        override val name = "Test"
        override val isConsole = false
        override fun hasPermission(permission: String) = true
        override fun send(message: net.kyori.adventure.text.Component) = Unit
    }
}
