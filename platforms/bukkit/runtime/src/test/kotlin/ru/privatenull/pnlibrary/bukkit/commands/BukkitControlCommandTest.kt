package ru.privatenull.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandSender
import ru.privatenull.pnlibrary.api.commands.CommandNodeKind
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.lang.reflect.Proxy

class BukkitControlCommandTest {
    @Test
    fun `control definition exposes admin command and actions through shared builder`() {
        val definition = BukkitControlCommand(
            plugin = proxy(Plugin::class.java),
            library = proxy(PnLibrary::class.java),
        ).definition()

        assertEquals("pn", definition.name)
        assertEquals("pnlibrary.admin", definition.permission)
        assertEquals(
            listOf("status", "updates", "check", "update", "restart", "debug", "support", "error", "error-repeat", "error-chain"),
            definition.root.children.map { it.name },
        )
        val update = definition.root.children.single { it.name == "update" }
        assertEquals(CommandNodeKind.ARGUMENT, update.children.single().kind)
        assertEquals("plugin", update.children.single().name)
        val restart = definition.root.children.single { it.name == "restart" }
        assertEquals("confirm", restart.children.single().name)
    }

    private class TestSender : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) = Unit
    }

    private fun <T> proxy(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, _ ->
            when (method.name) {
                "toString" -> type.simpleName
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> false
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Void.TYPE -> Unit
                    else -> null
                }
            }
        },
    )
}
