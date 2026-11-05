package org.pnfolder.pnlibrary.currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyActorType
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CurrencyTransactionJsonTest {
    private val full = CurrencyTransaction(
        id = UUID.randomUUID(),
        currency = CurrencyKey(PluginId.of("shop"), "coins"),
        type = CurrencyTransactionType.TRANSFER,
        status = CurrencyTransactionStatus.COMMITTED,
        amount = BigDecimal("12.50"),
        source = CurrencyAccount(UUID.randomUUID()),
        target = CurrencyAccount(UUID.randomUUID()),
        actor = CurrencyActor(CurrencyActorType.PLAYER, "Alex"),
        service = "pay",
        reason = "gift",
        metadata = mapOf("order" to "7"),
        sourceBalanceBefore = BigDecimal("100"),
        sourceBalanceAfter = BigDecimal("87.50"),
        targetBalanceBefore = BigDecimal("0"),
        targetBalanceAfter = BigDecimal("12.50"),
        createdAt = Instant.parse("2026-11-04T10:00:00Z"),
        failure = null,
        idempotencyKey = "key-1",
    )

    @Test
    fun `a full transaction survives a round trip`() {
        assertEquals(full, CurrencyTransactionJson.decode(CurrencyTransactionJson.encode(full)))
    }

    @Test
    fun `a transaction without optional parts survives a round trip`() {
        val bare = full.copy(
            source = null, target = null, reason = null, metadata = emptyMap(),
            sourceBalanceBefore = null, sourceBalanceAfter = null, targetBalanceBefore = null, targetBalanceAfter = null,
            idempotencyKey = null, failure = "not enough",
        )

        assertEquals(bare, CurrencyTransactionJson.decode(CurrencyTransactionJson.encode(bare)))
    }
}
