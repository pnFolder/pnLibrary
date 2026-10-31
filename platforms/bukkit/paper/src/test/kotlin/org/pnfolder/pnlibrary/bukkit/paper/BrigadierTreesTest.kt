package org.pnfolder.pnlibrary.bukkit.paper

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.command
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class BrigadierTreesTest {
    private val executed = mutableListOf<String>()
    private val suggested = mutableListOf<String>()

    private val invocation = object : NativeInvocation<String> {
        override fun execute(source: String, input: String) {
            executed += input
        }

        override fun suggest(source: String, input: String): CompletionStage<List<String>> {
            suggested += input
            return CompletableFuture.completedFuture(listOf("alpha", "beta"))
        }
    }

    private val demo = command("demo") {
        executes { }
        literal("set") {
            argument("amount", ArgumentType.integer(1, 10)) { executes { } }
        }
        literal("mode") {
            argument("kind", ArgumentType.choice("fast", "slow")) { executes { } }
        }
        literal("flag") {
            argument("on", ArgumentType.boolean()) { executes { } }
        }
    }

    private fun dispatcher(): CommandDispatcher<String> =
        CommandDispatcher<String>().also { it.root.addChild(BrigadierTrees.build(demo, invocation)) }

    @Test
    fun `the tree mirrors the portable command and runs executable routes`() {
        val dispatcher = dispatcher()

        dispatcher.execute("demo", "player")
        dispatcher.execute("demo set 5", "player")
        dispatcher.execute("demo mode fast", "player")

        assertEquals(listOf("demo", "demo set 5", "demo mode fast"), executed)
    }

    @Test
    fun `a number outside its bounds is rejected before the command runs`() {
        val dispatcher = dispatcher()

        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("demo set 99", "player") }
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("demo set abc", "player") }
        assertTrue(executed.isEmpty())
    }

    @Test
    fun `spellings the portable parser accepts are not rejected by the client tree`() {
        val dispatcher = dispatcher()

        dispatcher.execute("demo flag yes", "player")
        dispatcher.execute("demo flag off", "player")

        assertEquals(listOf("demo flag yes", "demo flag off"), executed)
    }

    @Test
    fun `argument suggestions come from the shared router with the full line typed so far`() {
        val dispatcher = dispatcher()

        val suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("demo mode ", "player")).get()

        assertEquals(listOf("alpha", "beta"), suggestions.list.map { it.text })
        assertEquals(listOf("demo mode "), suggested)
    }

    @Test
    fun `a subcommand the sender may not enter is not offered by tab`() {
        val guarded = command("guarded") {
            literal("open") { executes { } }
            literal("admin") {
                permission("demo.admin")
                executes { }
            }
        }
        val byPermission = object : NativeInvocation<Set<String>> {
            override fun execute(source: Set<String>, input: String) = Unit
            override fun suggest(source: Set<String>, input: String): CompletionStage<List<String>> =
                CompletableFuture.completedFuture(emptyList())

            override fun allowed(source: Set<String>, node: org.pnfolder.pnlibrary.api.commands.CommandNode): Boolean =
                node.permission == null || node.permission in source
        }
        val dispatcher = CommandDispatcher<Set<String>>()
        dispatcher.root.addChild(BrigadierTrees.build(guarded, byPermission))

        // The client tree is built from the nodes a source may use, so that is what decides what Tab offers.
        fun offered(source: Set<String>) =
            dispatcher.root.getChild("guarded").children.filter { it.canUse(source) }.map { it.name }.sorted()

        assertEquals(listOf("open"), offered(emptySet()))
        assertEquals(listOf("admin", "open"), offered(setOf("demo.admin")))
    }

    @Test
    fun `a command line is split into the label and its arguments`() {
        val typed = CommandLine.of("pnlibrary:pn events  abc", keepTrailing = false)
        assertEquals("pn", typed.label)
        assertEquals(listOf("events", "abc"), typed.arguments)

        val completing = CommandLine.of("/pn events view ", keepTrailing = true)
        assertEquals(listOf("events", "view", ""), completing.arguments)
    }
}
