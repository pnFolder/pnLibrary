package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.currency.CurrencyAccess
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId
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

    @Test
    fun `a provider that throws is reported to the owner as well as failing the call`() {
        val reported = mutableListOf<Pair<String, Throwable>>()
        val hub = CurrencyHub { message, error -> reported += message to error }
        val owner = hub.scope(PluginId.of("owner"), placeholders())
        val broken = object : CurrencyProvider, AutoCloseable {
            override val descriptor = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
            override fun balance(account: CurrencyAccount): CompletableFuture<BigDecimal> = error("balance broke")
            override fun close() = error("close broke")
        }
        val registration = owner.register("coins", broken)

        assertThrows(Exception::class.java) { registration.balance(java.util.UUID.randomUUID()).toCompletableFuture().get() }
        registration.close()

        assertEquals(listOf("balance broke", "close broke"), reported.map { it.second.message })
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
