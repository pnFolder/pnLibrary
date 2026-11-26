package org.pnfolder.pnlibrary.core.updates.restart;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One thing an administrator has to do somewhere in the network, shown until it is done. */
public final class ImportantNotice {
    private final String source;
    private final String address;
    private final String text;

    /**
     * @param source the proxy or server the notice is about
     * @param address where it can be reached, or null when unknown
     * @param text what has to be done
     */
    public ImportantNotice(@NotNull String source, @Nullable String address, @NotNull String text) {
        this.source = Objects.requireNonNull(source, "source");
        this.address = address;
        this.text = Objects.requireNonNull(text, "text");
    }

    public @NotNull String getSource() {
        return source;
    }

    public @Nullable String getAddress() {
        return address;
    }

    public @NotNull String getText() {
        return text;
    }

    /** The line shown in chat: who, where, and what to do. */
    public @NotNull String line() {
        return source + (address == null ? "" : " (" + address + ")") + ": " + text;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ImportantNotice)) {
            return false;
        }
        ImportantNotice that = (ImportantNotice) other;
        return source.equals(that.source) && Objects.equals(address, that.address) && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, address, text);
    }

    @Override
    public String toString() {
        return "ImportantNotice(" + line() + ")";
    }
}
