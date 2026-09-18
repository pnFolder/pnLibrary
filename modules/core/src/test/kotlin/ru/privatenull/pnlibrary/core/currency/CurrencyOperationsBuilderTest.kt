package ru.privatenull.pnlibrary.core.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.currency.CurrencyAccount
import ru.privatenull.pnlibrary.api.currency.CurrencyCapability
import ru.privatenull.pnlibrary.api.currency.CurrencyDeposits
import ru.privatenull.pnlibrary.api.currency.CurrencyDescriptor
import ru.privatenull.pnlibrary.api.currency.CurrencyResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.function.BiFunction
import java.util.function.Function

class CurrencyOperationsBuilderTest {
    @Test
    fun `build requires balance operation`() {
        val error = assertThrows(IllegalStateException::class.java) {
            CurrencyOperationsBuilder().build(descriptor(), "coins")
        }

        assertEquals("Currency coins requires a balance operation", error.message)
    }

    @Test
    fun `capabilities contain only configured operations`() {
        val provider = CurrencyOperationsBuilder()
            .balance(Function { BigDecimal.TEN })
            .deposit(BiFunction { _, amount -> CurrencyResult.success(current = amount) })
            .build(descriptor(), "coins")
        val capabilities = (provider as CurrencyCapabilitySource).declaredCapabilities

        assertTrue(CurrencyCapability.BALANCE in capabilities)
        assertTrue(CurrencyCapability.DEPOSIT in capabilities)
        assertFalse(CurrencyCapability.WITHDRAW in capabilities)
        assertEquals(BigDecimal.TEN, provider.balance(CurrencyAccount(UUID.randomUUID())).toCompletableFuture().join())
        assertTrue((provider as CurrencyDeposits).deposit(
            CurrencyAccount(UUID.randomUUID()),
            BigDecimal.ONE,
        ).toCompletableFuture().join().isSuccess)
    }

    @Test
    fun `extension preserves exact registered type`() {
        val extension = Runnable { }
        val provider = CurrencyOperationsBuilder()
            .balance(Function { BigDecimal.ZERO })
            .extension(Runnable::class.java, extension)
            .build(descriptor(), "coins")

        assertSame(extension, provider.extension(Runnable::class.java))
    }

    private fun descriptor() = CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN)
}
