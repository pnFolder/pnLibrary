package ru.privatenull.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.currency.CurrencyAccess
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyProvider
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistrationState
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class CurrencyHubTest {
    @Test
    fun `local alias resolves while owner-only currency stays hidden from another plugin`() {
        val hub = CurrencyHub()
        val owner = hub.scope(PluginId.of("owner"), placeholders())
        val consumer = hub.scope(PluginId.of("consumer"), placeholders())
        val registration = owner.register("coins", provider(), Consumer { it.aliases("money") })

        assertSame(registration, owner.get("money"))
        assertNull(consumer.get("owner:coins"))
        hub.close()
    }

    @Test
    fun `shared registration follows disable enable and scope close lifecycle`() {
        val hub = CurrencyHub()
        val owner = hub.scope(PluginId.of("owner"), placeholders())
        val consumer = hub.scope(PluginId.of("consumer"), placeholders())
        val registration = owner.register("coins", provider(), Consumer {
            it.access(CurrencyAccess.shared())
        })

        assertSame(registration, consumer.get("owner:coins"))
        registration.disable()
        assertEquals(CurrencyRegistrationState.DISABLED, registration.state)
        assertNull(consumer.get("owner:coins"))
        registration.enable()
        assertSame(registration, consumer.get("owner:coins"))

        owner.close()
        assertEquals(CurrencyRegistrationState.CLOSED, registration.state)
        assertNull(hub.get("owner:coins"))
        assertThrows(IllegalStateException::class.java) { registration.enable() }
        hub.close()
    }

    private fun provider() = object : CurrencyProvider, AutoCloseable {
        override val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
        override fun balance(account: CurrencyAccount) = CompletableFuture.completedFuture(BigDecimal.ZERO)
        override fun close() = Unit
    }

    private fun placeholders(): PlaceholderService = Proxy.newProxyInstance(
        PlaceholderService::class.java.classLoader,
        arrayOf(PlaceholderService::class.java),
    ) { _, method, _ ->
        if (method.name == "close") null else error("Unexpected placeholder call: ${method.name}")
    } as PlaceholderService
}
