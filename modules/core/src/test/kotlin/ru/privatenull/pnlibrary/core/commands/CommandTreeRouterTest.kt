package ru.privatenull.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.commands.command

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
        assertTrue(router.route(definition, context(listOf("builders", "secret", "delete"))) is CommandRoute.Denied)
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

        assertEquals("/amount <value>", (router.route(definition, context(emptyList())) as CommandRoute.Invalid).usage)
        assertEquals("/amount <value>", (router.route(definition, context(listOf("wrong"))) as CommandRoute.Invalid).usage)
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
