package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.command

class CommandTreeRouterTest {
    @Test
    fun `mixed tree prefers literals and accumulates typed values`() {
        val definition = command("group") {
            argument("group", ArgumentType.string()) {
                literal("page") { executes { } }
                literal("member") {
                    argument("page", ArgumentType.integer()) { executes { } }
                }
            }
        }
        val router = CommandTreeRouter()

        val member = router.route(definition, context(listOf("builders", "member", "12")))
        val literal = router.route(definition, context(listOf("builders", "page")))

        assertTrue(member is CommandRoute.Executable)
        assertEquals("builders", (member as CommandRoute.Executable).context.get<String>("group"))
        assertEquals(12, member.context.get<Int>("page"))
        assertTrue(literal is CommandRoute.Executable)
    }

    @Test
    fun `permissions and availability hide descendants and deny manual input`() {
        val definition = command("group") {
            argument("group", ArgumentType.string()) {
                literal("public") { executes { } }
                literal("secret") {
                    permission("groups.secret")
                    availableIf { it.get<String>("group") == "builders" }
                    literal("delete") { executes { } }
                }
            }
        }
        val router = CommandTreeRouter()

        val deniedSuggestions = router.suggest(definition, context(listOf("builders", "")))
            .toCompletableFuture().join()
        val allowedSuggestions = router.suggest(
            definition,
            context(listOf("builders", ""), permissions = setOf("groups.secret")),
        ).toCompletableFuture().join()

        assertEquals(listOf("public"), deniedSuggestions)
        assertEquals(listOf("public", "secret"), allowedSuggestions)
        val deniedUsage = (router.route(definition, context(listOf("builders"))) as CommandRoute.Problem).error as org.pnfolder.pnlibrary.api.commands.BadUsage
        assertEquals("/group <group> public", deniedUsage.usage)
        assertTrue(
            (router.route(definition, context(listOf("builders", "secret", "delete"))) as CommandRoute.Problem).error
                is org.pnfolder.pnlibrary.api.commands.NoPermission,
        )
        assertFalse(router.suggest(definition, context(listOf("other", "secret", ""), setOf("groups.secret")))
            .toCompletableFuture().join().contains("delete"))
    }

    @Test
    fun `argument suggestions see earlier values and partial input`() {
        val definition = command("group") {
            argument("group", ArgumentType.string()) {
                literal("member") {
                    argument("player", ArgumentType.string()) {
                        suggests { context ->
                            assertEquals("builders", context.get<String>("group"))
                            listOf("Steve", "Alex")
                        }
                        executes { }
                    }
                }
            }
        }

        val suggestions = CommandTreeRouter().suggest(
            definition,
            context(listOf("builders", "member", "St")),
        ).toCompletableFuture().join()

        assertEquals(listOf("Steve"), suggestions)
    }

    @Test
    fun `invalid or incomplete routes return usage`() {
        val definition = command("amount") {
            argument("value", ArgumentType.integer()) { executes { } }
        }
        val router = CommandTreeRouter()

        fun usageOf(arguments: List<String>) =
            ((router.route(definition, context(arguments)) as CommandRoute.Problem).error as org.pnfolder.pnlibrary.api.commands.BadUsage).usage
        assertEquals("/amount <value>", usageOf(emptyList()))
        assertEquals("/amount <value>", usageOf(listOf("wrong")))
    }

    @Test
    fun `a mounted command lives under another one and sees its own arguments`() {
        val seen = mutableListOf<List<String>>()
        val events = command("events") {
            permission("events.view")
            executes { seen += it.arguments }
            literal("view") {
                argument("id", ArgumentType.string()) { executes { seen += it.arguments } }
            }
        }
        val control = command("control") {
            literal("status") { permission("control.admin"); executes { } }
            mount("events", events)
        }
        val router = CommandTreeRouter()

        val root = router.route(control, context(listOf("events"), setOf("events.view")))
        val card = router.route(control, context(listOf("events", "view", "abc"), setOf("events.view")))
        (root as CommandRoute.Executable).node.execution.execute(root.context)
        (card as CommandRoute.Executable).node.execution.execute(card.context)

        assertEquals(listOf(emptyList(), listOf("view", "abc")), seen)
        // The permission of the mounted command stays its own, and the control command's does not leak into it.
        val denied = router.route(control, context(listOf("events"), setOf("control.admin")))
        assertTrue((denied as CommandRoute.Problem).error is org.pnfolder.pnlibrary.api.commands.NoPermission)
        val suggestions = router.suggest(control, context(listOf(""), setOf("events.view"))).toCompletableFuture().join()
        assertEquals(listOf("events"), suggestions)
    }

    @Test
    fun `mounting under a name that is taken is refused`() {
        val other = command("other") { executes { } }

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            command("control") {
                literal("events") { executes { } }
                mount("events", other)
            }
        }
    }

    private fun context(arguments: List<String>, permissions: Set<String> = emptySet()) = CommandContext(
        sender = TestSender(permissions),
        arguments = arguments,
        invokedAlias = "group",
        currentInput = arguments.lastOrNull().orEmpty(),
    )

    private class TestSender(private val permissions: Set<String>) : CommandSender {
        override val id = "sender"
        override val name = "Sender"
        override val isConsole = false
        override fun hasPermission(permission: String) = permission in permissions
        override fun send(message: Component) = Unit
    }
}
