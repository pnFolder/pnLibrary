package org.pnfolder.pnlibrary.core.settings;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** How the server restarts after an update is staged: whether to restart alone, and when it is quiet enough. */
public final class RestartSettings {
    private final boolean auto;
    private final int maxPlayers;
    private final Duration quietTime;

    /** Settings with every value given. */
    public RestartSettings(boolean auto, int maxPlayers, @NotNull Duration quietTime) {
        this.auto = auto;
        this.maxPlayers = maxPlayers;
        this.quietTime = Objects.requireNonNull(quietTime, "quietTime");
    }

    /** Settings with every value at its default: administrators are always asked, the server never restarts alone. */
    public RestartSettings() {
        this(false, 0, Duration.ofMinutes(2));
    }

    /** Whether the server restarts itself when few enough players are online. */
    public boolean getAuto() {
        return auto;
    }

    /** The most players that still count as few, for the automatic restart and for the button. */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /** How long there must be few players, without a break, before the restart. */
    public @NotNull Duration getQuietTime() {
        return quietTime;
    }

    public @NotNull RestartSettings withAuto(boolean value) {
        return new RestartSettings(value, maxPlayers, quietTime);
    }

    public @NotNull RestartSettings withMaxPlayers(int value) {
        return new RestartSettings(auto, value, quietTime);
    }

    public @NotNull RestartSettings withQuietTime(@NotNull Duration value) {
        return new RestartSettings(auto, maxPlayers, value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RestartSettings)) {
            return false;
        }
        RestartSettings that = (RestartSettings) other;
        return auto == that.auto && maxPlayers == that.maxPlayers && quietTime.equals(that.quietTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auto, maxPlayers, quietTime);
    }

    @Override
    public String toString() {
        return "RestartSettings(auto=" + auto + ", maxPlayers=" + maxPlayers
            + ", quietTime=" + quietTime + ")";
    }
}
