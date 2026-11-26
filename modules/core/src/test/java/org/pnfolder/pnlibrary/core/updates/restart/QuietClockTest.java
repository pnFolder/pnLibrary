package org.pnfolder.pnlibrary.core.updates.restart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuietClockTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void countsFromTheFirstQuietLookAndStartsOverWhenTheServerFillsUp() {
        QuietClock clock = new QuietClock();

        assertEquals(Duration.ZERO, clock.observe(2, 3, START));
        assertEquals(Duration.ofSeconds(90), clock.observe(3, 3, START.plusSeconds(90)));
        assertEquals(Duration.ZERO, clock.observe(4, 3, START.plusSeconds(100)));
        assertEquals(Duration.ZERO, clock.observe(1, 3, START.plusSeconds(110)));
        assertEquals(Duration.ofSeconds(10), clock.observe(1, 3, START.plusSeconds(120)));
    }

    @Test
    void resetForgetsWhatWasSeen() {
        QuietClock clock = new QuietClock();
        clock.observe(0, 0, START);
        clock.reset();

        assertEquals(Duration.ZERO, clock.observe(0, 0, START.plusSeconds(60)));
    }
}
