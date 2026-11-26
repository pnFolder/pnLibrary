package org.pnfolder.pnlibrary.core.menu.data;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** What came of changing a setting from the menu: the message is the reason when not ok, otherwise the new value. */
public final class SettingOutcome {
    private final boolean ok;
    private final String message;
    private final boolean live;

    public SettingOutcome(boolean ok, @NotNull String message, boolean live) {
        this.ok = ok;
        this.message = message;
        this.live = live;
    }

    public SettingOutcome(boolean ok, @NotNull String message) {
        this(ok, message, true);
    }

    public boolean isOk() {
        return ok;
    }

    public @NotNull String getMessage() {
        return message;
    }

    public boolean isLive() {
        return live;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SettingOutcome)) {
            return false;
        }
        SettingOutcome that = (SettingOutcome) other;
        return ok == that.ok && message.equals(that.message) && live == that.live;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, message, live);
    }

    @Override
    public String toString() {
        return "SettingOutcome(ok=" + ok + ", message=" + message + ", live=" + live + ")";
    }
}
