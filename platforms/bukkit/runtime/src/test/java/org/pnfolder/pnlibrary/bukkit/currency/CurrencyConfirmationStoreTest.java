package org.pnfolder.pnlibrary.bukkit.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

class CurrencyConfirmationStoreTest {
    private final CurrencyConfirmationStore store = new CurrencyConfirmationStore();

    private CurrencyConfirmationStore.PendingOperation add(int timeoutSeconds) {
        return store.add(timeoutSeconds, "coins", "give 5", "console", CompletableFuture::new, result -> { });
    }

    @Test
    void aStoredOperationIsTakenOnceAndItsCodeIgnoresCase() {
        CurrencyConfirmationStore.PendingOperation operation = add(60);

        assertEquals(10, operation.token.length());
        assertNotNull(store.take(operation.token.toLowerCase(Locale.ROOT)));
        assertNull(store.take(operation.token));
    }

    @Test
    void everyOperationGetsItsOwnCode() {
        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 200; index++) {
            tokens.add(add(60).token);
        }

        assertEquals(200, tokens.size());
    }

    @Test
    void theQueueRefusesNewOperationsWhenItIsFull() {
        for (int index = 0; index < 1_000; index++) {
            assertNotNull(add(60));
        }

        assertNull(add(60));
    }

    @Test
    void expiredOperationsAreDiscardedAndLiveOnesStay() {
        CurrencyConfirmationStore.PendingOperation expired = add(-1);
        CurrencyConfirmationStore.PendingOperation live = add(60);

        store.discardExpired();

        assertNull(store.take(expired.token));
        assertTrue(store.take(live.token) != null);
    }
}
