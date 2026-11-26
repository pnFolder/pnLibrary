package org.pnfolder.pnlibrary.api.placeholders.info;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** One format as an administrator sees it in lists. */
public final class FormatInfo {
    private final String name;
    private final String input;

    /**
     * Creates the description.
     *
     * @param name the word after the bar in {@code {value|name}}
     * @param input what kind of value it accepts, such as {@code Number}
     */
    public FormatInfo(@NotNull String name, @NotNull String input) {
        this.name = Objects.requireNonNull(name);
        this.input = Objects.requireNonNull(input);
    }

    /** The word after the bar. */
    @NotNull
    public String getName() {
        return name;
    }

    /** What kind of value it accepts. */
    @NotNull
    public String getInput() {
        return input;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FormatInfo)) {
            return false;
        }
        FormatInfo that = (FormatInfo) other;
        return name.equals(that.name) && input.equals(that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, input);
    }

    @NotNull
    @Override
    public String toString() {
        return "FormatInfo(name=" + name + ", input=" + input + ")";
    }
}
