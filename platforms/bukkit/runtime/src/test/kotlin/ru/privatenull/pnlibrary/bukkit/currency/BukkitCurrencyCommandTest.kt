package ru.privatenull.pnlibrary.bukkit.currency

import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.commands.CommandNodeKind
import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.currency.CurrencyProvider
import ru.privatenull.pnlibrary.api.currency.CurrencyProviderRegistry
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistration
import ru.privatenull.pnlibrary.api.currency.CurrencyAccess
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.lang.reflect.Proxy

class BukkitCurrencyCommandTest {
    @Test
    fun `currency definition exposes arbitrary continuation through portable tree`() {
        val definition = CurrencyCommandExecutor(
            plugin = proxy(Plugin::class.java),
            currencies = EmptyCurrencies,
        ).definition()

        assertEquals("pncurrency", definition.name)
        assertEquals(setOf("pncurrencies"), definition.aliases)
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
