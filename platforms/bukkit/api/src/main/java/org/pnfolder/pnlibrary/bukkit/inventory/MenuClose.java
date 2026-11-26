package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

/** Normal close callback containing the session that just finished. */
public final class MenuClose {
    private final MenuSession session;

    public MenuClose(@NotNull MenuSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Finished session whose inventory remains available for final inspection. */
    @NotNull
    public MenuSession getSession() {
        return session;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MenuClose && session.equals(((MenuClose) other).session);
    }

    @Override
    public int hashCode() {
        return session.hashCode();
    }

    @Override
    public String toString() {
        return "MenuClose(session=" + session + ")";
    }
}
