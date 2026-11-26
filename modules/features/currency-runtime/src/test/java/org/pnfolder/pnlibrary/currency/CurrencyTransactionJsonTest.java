package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyActorType;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class CurrencyTransactionJsonTest {
    private final CurrencyTransaction full = new CurrencyTransaction(UUID.randomUUID(),
            new CurrencyKey(PluginId.of("shop"), "coins"), CurrencyTransactionType.TRANSFER, CurrencyTransactionStatus.COMMITTED,
            new BigDecimal("12.50"), new CurrencyAccount(UUID.randomUUID()), new CurrencyAccount(UUID.randomUUID()),
            new CurrencyActor(CurrencyActorType.PLAYER, "Alex"), "pay", "gift", Collections.singletonMap("order", "7"),
            new BigDecimal("100"), new BigDecimal("87.50"), new BigDecimal("0"), new BigDecimal("12.50"),
            Instant.parse("2026-11-04T10:00:00Z"), null, "key-1");

    @Test
    void aFullTransactionSurvivesARoundTrip() {
        assertEquals(full, CurrencyTransactionJson.decode(CurrencyTransactionJson.encode(full)));
    }

    @Test
    void aTransactionWithoutOptionalPartsSurvivesARoundTrip() {
        CurrencyTransaction bare = full.toBuilder()
                .source(null).target(null).reason(null).metadata(Collections.<String, String>emptyMap())
                .sourceBalance(null, null).targetBalance(null, null)
                .idempotencyKey(null).failure("not enough")
                .build();

        assertEquals(bare, CurrencyTransactionJson.decode(CurrencyTransactionJson.encode(bare)));
    }
}
