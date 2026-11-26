package org.pnfolder.pnlibrary.updates.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

class FreezePolicyTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("accepts only durations from one minute through five days")
    void acceptsOnlyDurationsFromOneMinuteThroughFiveDays() {
        assertEquals(Duration.ofMinutes(1), FreezeDuration.parse("1m"));
        assertEquals(Duration.ofHours(2), FreezeDuration.parse("2h"));
        assertEquals(Duration.ofDays(5), FreezeDuration.parse("5d"));
        for (String value : new String[] {"0m", "6d", "30d", "forever", "permanent", "1s"}) {
            assertThrows(IllegalArgumentException.class, () -> FreezeDuration.parse(value), value);
        }
    }

    @Test
    @DisplayName("a pause saved under the older thirty day limit ends within five days")
    void aPauseSavedUnderTheOlderThirtyDayLimitEndsWithinFiveDays() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-17T10:00:00Z"));
        Path file = directory.resolve("preferences.json");
        String json = "{\"freezes\":{\"pnmarket\":" + clock.instant().plus(Duration.ofDays(30)).toEpochMilli() + "}}";
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        assertEquals(Duration.ofDays(5), new FreezeStore(new UpdatePreferencesStore(file), clock).remaining(ProductId.of("pnmarket")));
    }

    @Test
    @DisplayName("persists absolute expiry and does not restart duration")
    void persistsAbsoluteExpiryAndDoesNotRestartDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-17T10:00:00Z"));
        Path file = directory.resolve("preferences.json");
        ProductId market = ProductId.of("pnmarket");
        new FreezeStore(new UpdatePreferencesStore(file), clock).freeze(market, Duration.ofHours(2));

        clock.advance(Duration.ofMinutes(30));
        FreezeStore restarted = new FreezeStore(new UpdatePreferencesStore(file), clock);
        assertEquals(Duration.ofMinutes(90), restarted.remaining(market));
    }

    @Test
    @DisplayName("expiry removes only the elapsed component")
    void expiryRemovesOnlyTheElapsedComponent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-17T10:00:00Z"));
        FreezeStore store = new FreezeStore(new UpdatePreferencesStore(directory.resolve("preferences.json")), clock);
        ProductId market = ProductId.of("pnmarket");
        ProductId auth = ProductId.of("pnauth");
        store.freeze(market, Duration.ofMinutes(1));
        store.freeze(auth, Duration.ofHours(1));

        clock.advance(Duration.ofMinutes(2));
        assertFalse(store.isFrozen(market));
        assertTrue(store.isFrozen(auth));
        assertEquals(Collections.singleton(auth), store.active().keySet());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
