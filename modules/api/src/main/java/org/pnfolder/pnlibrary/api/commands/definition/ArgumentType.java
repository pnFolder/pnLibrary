package org.pnfolder.pnlibrary.api.commands.definition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;
import org.pnfolder.pnlibrary.api.commands.error.CommandError;
import org.pnfolder.pnlibrary.api.commands.error.InvalidArgument;
import org.pnfolder.pnlibrary.api.commands.error.PlayerNotFound;

/** Converts one command token into a typed value; the factories below are the built-in parsers. */
@FunctionalInterface
public interface ArgumentType<T> {
    /** Parses one raw command token, returning {@code null} when it is invalid. */
    @Nullable
    T parse(@NotNull String value);

    /**
     * Describes the accepted tokens for platforms that validate and suggest them natively. Platforms without native
     * support ignore it; the portable {@link #parse} always decides in the end.
     */
    @NotNull
    default ArgumentSpec getSpec() {
        return ArgumentSpec.TEXT;
    }

    /**
     * The error reported when {@link #parse} returns {@code null}: by default an {@link InvalidArgument}. A player
     * argument answers {@link PlayerNotFound}, and a type of your own can return an error class of its own so that it
     * gets its own answer.
     *
     * @param usage the usage hint of the command
     * @param argument the name of the argument that failed
     * @param value what was typed
     */
    @NotNull
    default CommandError error(@NotNull String usage, @NotNull String argument, @NotNull String value) {
        return new InvalidArgument(usage, argument, value);
    }

    /** Returns the parser that preserves the input token as a string. */
    @NotNull
    static ArgumentType<String> string() {
        return TypedArgument.STRING;
    }

    /**
     * Returns the argument that takes the rest of the line, such as a clan name or a search phrase with spaces. It must
     * be the last node of its branch: the router hands it every remaining word joined by single spaces.
     */
    @NotNull
    static ArgumentType<String> greedyString() {
        return TypedArgument.GREEDY_STRING;
    }

    /** Returns the base-10 32-bit integer parser. */
    @NotNull
    static ArgumentType<Integer> integer() {
        return TypedArgument.INTEGER;
    }

    /** Returns the 32-bit integer parser that accepts only values from {@code minimum} to {@code maximum}. */
    @NotNull
    static ArgumentType<Integer> integer(final int minimum, final int maximum) {
        TypedArgument.requireOrdered(minimum <= maximum);
        return new TypedArgument<Integer>(ArgumentSpec.integer(minimum, maximum), token -> {
            Integer number = TypedArgument.parseInt(token);
            return number != null && number >= minimum && number <= maximum ? number : null;
        });
    }

    /** Returns the base-10 64-bit integer parser. */
    @NotNull
    static ArgumentType<Long> longInteger() {
        return TypedArgument.LONG;
    }

    /** Returns the 64-bit integer parser that accepts only values from {@code minimum} to {@code maximum}. */
    @NotNull
    static ArgumentType<Long> longInteger(final long minimum, final long maximum) {
        TypedArgument.requireOrdered(minimum <= maximum);
        return new TypedArgument<Long>(ArgumentSpec.longInteger(minimum, maximum), token -> {
            Long number = TypedArgument.parseLong(token);
            return number != null && number >= minimum && number <= maximum ? number : null;
        });
    }

    /** Returns the arbitrary-precision decimal parser. */
    @NotNull
    static ArgumentType<BigDecimal> decimal() {
        return TypedArgument.DECIMAL;
    }

    /** Returns the decimal parser that accepts only values from {@code minimum} to {@code maximum}. */
    @NotNull
    static ArgumentType<BigDecimal> decimal(final double minimum, final double maximum) {
        TypedArgument.requireOrdered(minimum <= maximum);
        return new TypedArgument<BigDecimal>(ArgumentSpec.decimal(minimum, maximum), token -> {
            BigDecimal number = TypedArgument.parseDecimal(token);
            return number != null && number.doubleValue() >= minimum && number.doubleValue() <= maximum ? number : null;
        });
    }

    /** Returns a case-insensitive boolean parser accepting common aliases. */
    @NotNull
    static ArgumentType<Boolean> bool() {
        return TypedArgument.BOOLEAN;
    }

    /** Returns a case-insensitive parser for constants declared by enum {@code type}. */
    @NotNull
    static <E extends Enum<E>> ArgumentType<E> enumeration(@NotNull final Class<E> type) {
        final E[] constants = type.getEnumConstants();
        List<String> names = new ArrayList<String>();
        for (E constant : constants) {
            names.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return new TypedArgument<E>(ArgumentSpec.choice(names), token -> {
            for (E constant : constants) {
                if (constant.name().equalsIgnoreCase(token)) {
                    return constant;
                }
            }
            return null;
        });
    }

    /**
     * Returns an argument that finds an online player by the typed name with {@code lookup}. A name nobody answers to
     * is reported as {@link PlayerNotFound}, which the library answers with "player is not online" unless a handler
     * says otherwise.
     */
    @NotNull
    static ArgumentType<OnlinePlayer> player(@NotNull final Function<String, OnlinePlayer> lookup) {
        return new ArgumentType<OnlinePlayer>() {
            @Nullable
            @Override
            public OnlinePlayer parse(@NotNull String value) {
                return lookup.apply(value);
            }

            @NotNull
            @Override
            public CommandError error(@NotNull String usage, @NotNull String argument, @NotNull String value) {
                return new PlayerNotFound(usage, argument, value);
            }
        };
    }

    /**
     * Returns a case-insensitive parser for one of {@code values}; the parsed value is the spelling declared here.
     * Platforms with native commands show the values as suggestions and reject anything else early.
     */
    @NotNull
    static ArgumentType<String> choice(@NotNull String... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("choice needs at least one value");
        }
        final List<String> declared = new ArrayList<String>(Arrays.asList(values));
        return new TypedArgument<String>(ArgumentSpec.choice(declared), token -> {
            for (String value : declared) {
                if (value.equalsIgnoreCase(token)) {
                    return value;
                }
            }
            return null;
        });
    }
}
