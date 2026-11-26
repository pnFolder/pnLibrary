package org.pnfolder.pnlibrary.core.cooldowns;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryCooldownsTest {
    @Test
    void allOperationsUseTheSameTrimmedCaseInsensitiveActionKey() {
        InMemoryCooldowns service = new InMemoryCooldowns();
        UUID player = UUID.randomUUID();

        assertTrue(service.acquire(player, "  LOGIN  ", Duration.ofMinutes(1)).getAllowed());
        assertTrue(service.has(player, "login"));
        assertTrue(service.reset(player, " Login "));
        assertFalse(service.has(player, "LOGIN"));
    }

    @Test
    void cleanupRemainsIdempotentAfterCloseWhileReadsReject() {
        InMemoryCooldowns service = new InMemoryCooldowns();
        UUID player = UUID.randomUUID();
        service.set(player, "example", Duration.ofMinutes(1));

        service.close();

        assertDoesNotThrow(() -> service.reset(player, "example"));
        assertDoesNotThrow(() -> service.clear());
        assertThrows(IllegalStateException.class, () -> service.remaining(player, "example"));
    }

    @Test
    void activeListsTheCooldownsOfOnePlayerAndClearRemovesOnlyThatPlayers() {
        InMemoryCooldowns service = new InMemoryCooldowns();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        service.set(player, "kit", Duration.ofMinutes(10));
        service.set(player, "home", Duration.ofMinutes(1));
        service.set(other, "kit", Duration.ofMinutes(1));

        assertEquals(Arrays.asList("kit", "home"), new ArrayList<>(service.active(player).keySet()));
        assertEquals(2, service.clear(player));
        assertTrue(service.active(player).isEmpty());
        assertTrue(service.has(other, "kit"));
    }

    @Test
    void expiredEntriesOfPlayersNobodyAsksAboutAreSweptAway() throws InterruptedException {
        InMemoryCooldowns service = new InMemoryCooldowns();
        for (int i = 0; i < 300; i++) {
            service.set(UUID.randomUUID(), "once", Duration.ofNanos(1));
        }
        Thread.sleep(5);
        for (int i = 0; i < 300; i++) {
            service.set(UUID.randomUUID(), "later", Duration.ofMinutes(1));
        }

        assertTrue(service.getSize() <= 300);
    }
}
