package org.pnfolder.pnlibrary.bukkit.currency

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandNodeKind
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.currency.CurrencyAccess
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import java.lang.reflect.Proxy
import org.bukkit.command.CommandSender as NativeCommandSender

class BukkitCurrencyCommandTest {
    @Test
    fun `currency definition exposes arbitrary continuation through portable tree`() {
        val definition = CurrencyCommandExecutor(
            plugin = proxy(Plugin::class.java),
            currencies = EmptyCurrencies,
        ).definition()

        assertEquals("money", definition.name)
        assertEquals(listOf("list", "confirm", "cancel"), definition.root.children.take(3).map { it.name })
        val currency = definition.root.children.single { it.kind == CommandNodeKind.ARGUMENT }
        assertEquals("currency", currency.name)
        assertEquals(
            listOf("balance", "add", "take", "set", "reset", "pay", "history"),
            currency.children.map { it.name },
        )
        assertTrue(currency.children.single { it.name == "add" }.children
            .single { it.name == "player" }.children.single { it.name == "amount" }.isExecutable)
    }

    @Test
    fun `help is a Russian page of commands`() {
        val messages = run("help")

        assertEquals("", messages.first())
        assertEquals("Валюты  помощь", messages[1])
        assertTrue("<<Команды>>" in messages.map { it.trim() }, messages.toString())
        assertTrue("    /pn money list - список валют" in messages, messages.toString())
        assertTrue(messages.any { it.contains("/pn money <валюта> pay <игрок> <сумма> - перевести игроку") })
    }

    @Test
    fun `an unknown currency and an empty list are notices`() {
        assertEquals(listOf("  ! Валюта не найдена или недоступна: test:gems"), run("test:gems"))
        assertEquals(listOf("  * Сейчас нет доступных валют."), run("list"))
    }

    @Test
    fun `missing permission names the permission`() {
        assertEquals(listOf("  ! Недостаточно прав.", "    Нужно право pnlibrary.currency.list"), run("list", permitted = false))
    }

    /** Runs `/pn money` from the console and returns the replies as plain text. */
    private fun run(vararg arguments: String, permitted: Boolean = true): List<String> {
        val console = ConsoleAudience(permitted)
        val sender = BukkitCommandSender(proxy(NativeCommandSender::class.java), console)
        CurrencyCommandExecutor(proxy(Plugin::class.java), EmptyCurrencies).definition().execution
            .execute(CommandContext(sender, arguments.toList(), "money", arguments.last()))
            .toCompletableFuture().join()
        return console.messages.map { LegacyComponentSerializer.legacySection().serialize(it).replace(Regex("§."), "") }
    }

    private class ConsoleAudience(private val permitted: Boolean) : AudienceSender {
        val messages = mutableListOf<Component>()
        override val id = "console"
        override val name = "CONSOLE"
        override val isConsole = true
        override fun hasPermission(permission: String) = permitted
        override fun sendMessage(text: Component) {
            messages += text
        }
        override fun actionBar(text: Component) = sendMessage(text)
        override fun playSound(sound: Sound) = false
    }

    private object EmptyCurrencies : CurrencyProviderRegistry {
        override fun register(owner: PluginId, name: String, provider: CurrencyProvider, access: CurrencyAccess): CurrencyRegistration =
            error("not used")
        override fun get(reference: String): Currency? = null
        override fun all(): List<Currency> = emptyList()
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
