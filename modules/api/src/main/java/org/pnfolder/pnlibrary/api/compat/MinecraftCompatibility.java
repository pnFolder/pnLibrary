package org.pnfolder.pnlibrary.api.compat;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The result of the Minecraft version check of one module, as {@code /pn modules view} and the menu show it. */
public final class MinecraftCompatibility {
    /** A module that declared nothing. */
    public static final MinecraftCompatibility NOT_DECLARED =
        new MinecraftCompatibility(MinecraftSupport.NOT_DECLARED, null, null);

    private final MinecraftSupport support;
    private final String declared;
    private final String server;
    private final boolean forced;
    private final boolean blocked;
    private final String note;

    /**
     * @param support how the server's version stands against the declaration
     * @param declared the declared versions in words, such as {@code 1.16.5-1.21.4, 1.8.8}, or {@code null} when
     *     nothing was declared
     * @param server the server's Minecraft version, or {@code null} when it cannot be read
     * @param forced an administrator chose to start the module anyway
     * @param blocked the module does not start because of this check
     * @param note the sentence that explains the result, or {@code null} when all is well
     */
    public MinecraftCompatibility(@NotNull MinecraftSupport support, @Nullable String declared,
                                  @Nullable String server, boolean forced, boolean blocked, @Nullable String note) {
        this.support = Objects.requireNonNull(support, "support");
        this.declared = declared;
        this.server = server;
        this.forced = forced;
        this.blocked = blocked;
        this.note = note;
    }

    /** A result that is neither forced nor blocked and has no note. */
    public MinecraftCompatibility(@NotNull MinecraftSupport support, @Nullable String declared,
                                  @Nullable String server) {
        this(support, declared, server, false, false, null);
    }

    /** How the server's version stands against the declaration. */
    public @NotNull MinecraftSupport getSupport() {
        return support;
    }

    /** The declared versions in words, or {@code null} when nothing was declared. */
    public @Nullable String getDeclared() {
        return declared;
    }

    /** The server's Minecraft version, or {@code null} when it cannot be read. */
    public @Nullable String getServer() {
        return server;
    }

    /** Whether an administrator chose to start the module anyway. */
    public boolean isForced() {
        return forced;
    }

    /** Whether the module does not start because of this check. */
    public boolean isBlocked() {
        return blocked;
    }

    /** The sentence that explains the result, or {@code null} when all is well. */
    public @Nullable String getNote() {
        return note;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MinecraftCompatibility)) {
            return false;
        }
        MinecraftCompatibility that = (MinecraftCompatibility) other;
        return forced == that.forced && blocked == that.blocked && support == that.support
            && Objects.equals(declared, that.declared) && Objects.equals(server, that.server)
            && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(support, declared, server, forced, blocked, note);
    }

    @Override
    public String toString() {
        return "MinecraftCompatibility(support=" + support + ", declared=" + declared + ", server=" + server
            + ", forced=" + forced + ", blocked=" + blocked + ", note=" + note + ")";
    }
}
