package org.pnfolder.pnlibrary.core.updates.restart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;
import org.pnfolder.pnlibrary.core.updates.restart.AutoRestartRule.Verdict;

class AutoRestartRuleTest {
    private static final Duration LONG = Duration.ofHours(1);
    private final RestartSettings on = new RestartSettings(true, 3, Duration.ofMinutes(2));

    private Verdict check(RestartSettings settings, int players, Duration quiet, Duration uptime,
                          boolean rolledBack) {
        return AutoRestartRule.check(settings, players, quiet, uptime, rolledBack);
    }

    @Test
    void doesNothingWhileAutomaticRestartIsOff() {
        assertEquals(Verdict.OFF, check(on.withAuto(false), 0, LONG, LONG, false));
    }

    @Test
    void restartsWhenFewPlayersStayedLongEnoughAndTheServerIsOldEnough() {
        assertEquals(Verdict.RESTART, check(on, 3, Duration.ofMinutes(2), AutoRestartRule.MIN_UPTIME, false));
    }

    @Test
    void waitsWhileThereAreTooManyPlayersOrItWasNotQuietLongEnough() {
        assertEquals(Verdict.WAIT, check(on, 4, LONG, LONG, false));
        assertEquals(Verdict.WAIT, check(on, 1, Duration.ofSeconds(119), LONG, false));
    }

    @Test
    void neverRestartsInTheFirstTenMinutesAfterStart() {
        assertEquals(Verdict.WAIT, check(on, 0, LONG, Duration.ofMinutes(9), false));
    }

    @Test
    void asksInsteadAfterARollbackEvenWhenEverythingElseAllowsIt() {
        assertEquals(Verdict.ROLLED_BACK, check(on, 0, LONG, LONG, true));
    }

    @Test
    void zeroPlayersAreAllowedByTheDefaultLimit() {
        RestartSettings alone = new RestartSettings(true, 0, Duration.ofSeconds(10));
        assertEquals(Verdict.RESTART, check(alone, 0, Duration.ofSeconds(10), LONG, false));
        assertEquals(Verdict.WAIT, check(alone, 1, LONG, LONG, false));
    }
}
