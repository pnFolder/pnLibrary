package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a platform needs to know about an {@link ArgumentType} to validate and suggest it natively, such as the
 * Brigadier tree of a modern Paper server, before the portable parser runs.
 */
public final class ArgumentSpec {
    /** Any single word. */
    public static final ArgumentSpec TEXT = new ArgumentSpec(Kind.TEXT, null, null, Collections.<String>emptyList());
    /** Every remaining word of the line, joined by single spaces. */
    public static final ArgumentSpec GREEDY = new ArgumentSpec(Kind.GREEDY, null, null, Collections.<String>emptyList());
    /** A true/false token. */
    public static final ArgumentSpec BOOLEAN = new ArgumentSpec(Kind.BOOLEAN, null, null, Collections.<String>emptyList());

    private final Kind kind;
    private final Double minimum;
    private final Double maximum;
    private final List<String> choices;

    private ArgumentSpec(Kind kind, Double minimum, Double maximum, List<String> choices) {
        this.kind = kind;
        this.minimum = minimum;
        this.maximum = maximum;
        this.choices = Collections.unmodifiableList(new ArrayList<String>(choices));
    }

    /** A whole number of any size. */
    @NotNull
    public static ArgumentSpec integer() {
        return integer(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** A whole number between {@code minimum} and {@code maximum}. */
    @NotNull
    public static ArgumentSpec integer(int minimum, int maximum) {
        return new ArgumentSpec(Kind.INTEGER, (double) minimum, (double) maximum, Collections.<String>emptyList());
    }

    /** A 64-bit whole number of any size. */
    @NotNull
    public static ArgumentSpec longInteger() {
        return longInteger(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /** A 64-bit whole number between {@code minimum} and {@code maximum}. */
    @NotNull
    public static ArgumentSpec longInteger(long minimum, long maximum) {
        return new ArgumentSpec(Kind.LONG, (double) minimum, (double) maximum, Collections.<String>emptyList());
    }

    /** A decimal number of any size. */
    @NotNull
    public static ArgumentSpec decimal() {
        return decimal(-Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** A decimal number between {@code minimum} and {@code maximum}. */
    @NotNull
    public static ArgumentSpec decimal(double minimum, double maximum) {
        return new ArgumentSpec(Kind.DECIMAL, minimum, maximum, Collections.<String>emptyList());
    }

    /** One of {@code choices}. */
    @NotNull
    public static ArgumentSpec choice(@NotNull List<String> choices) {
        return new ArgumentSpec(Kind.CHOICE, null, null, choices);
    }

    /** The shape of the accepted token. */
    @NotNull
    public Kind getKind() {
        return kind;
    }

    /** Lowest accepted number for numeric kinds, or {@code null} for no bound. */
    @Nullable
    public Double getMinimum() {
        return minimum;
    }

    /** Highest accepted number for numeric kinds, or {@code null} for no bound. */
    @Nullable
    public Double getMaximum() {
        return maximum;
    }

    /** The accepted values of a {@link Kind#CHOICE}, also offered as suggestions. */
    @NotNull
    public List<String> getChoices() {
        return choices;
    }

    /** The shape of a token. */
    public enum Kind {
        /** Any single word. */
        TEXT,
        /** A 32-bit whole number. */
        INTEGER,
        /** A 64-bit whole number. */
        LONG,
        /** A decimal number. */
        DECIMAL,
        /** A true/false token. */
        BOOLEAN,
        /** One of a fixed list of words. */
        CHOICE,
        /** The rest of the line. */
        GREEDY
    }
}
