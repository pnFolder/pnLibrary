package org.pnfolder.pnlibrary.core.updates.restart;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.settings.RestartSettings;

/** Decides whether the server may restart by itself now; it knows nothing about the platform. */
public final class AutoRestartRule {
    /** The server is left alone this long after it started, so a failing start does not turn into a restart loop. */
    public static final Duration MIN_UPTIME = Duration.ofMinutes(10);

    /** The answer for one look at the server. */
    public enum Verdict {
        /** Automatic restart is switched off. */
        OFF,
        /** Not yet: too early after start, too many players or not quiet for long enough. */
        WAIT,
        /** Restart now. */
        RESTART,
        /** The plan was rolled back; a person decides about that restart. */
        ROLLED_BACK
    }

    private AutoRestartRule() {
    }

    /**
     * @param players how many players are online now
     * @param quiet how long there have been few enough of them, from {@link QuietClock}
     * @param uptime how long the server has been running
     * @param rolledBack whether the plan that waits for the restart is a rollback
     */
    public static @NotNull Verdict check(@NotNull RestartSettings settings, int players, @NotNull Duration quiet,
                                         @NotNull Duration uptime, boolean rolledBack) {
        if (!settings.getAuto()) {
            return Verdict.OFF;
        }
        if (rolledBack) {
            return Verdict.ROLLED_BACK;
        }
        boolean calm = players <= settings.getMaxPlayers() && quiet.compareTo(settings.getQuietTime()) >= 0;
        return calm && uptime.compareTo(MIN_UPTIME) >= 0 ? Verdict.RESTART : Verdict.WAIT;
    }
}
