package org.pnfolder.pnlibrary.api.commands.definition;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An {@link ArgumentType} made of a parsing function and a spec; the building block of the built-in types. */
final class TypedArgument<T> implements ArgumentType<T> {
    static final ArgumentType<String> STRING = new TypedArgument<String>(ArgumentSpec.TEXT, token -> token);
    static final ArgumentType<String> GREEDY_STRING =
            new TypedArgument<String>(ArgumentSpec.GREEDY, token -> token.trim().isEmpty() ? null : token);
    static final ArgumentType<Integer> INTEGER = new TypedArgument<Integer>(ArgumentSpec.integer(), TypedArgument::parseInt);
    static final ArgumentType<Long> LONG = new TypedArgument<Long>(ArgumentSpec.longInteger(), TypedArgument::parseLong);
    static final ArgumentType<BigDecimal> DECIMAL =
            new TypedArgument<BigDecimal>(ArgumentSpec.decimal(), TypedArgument::parseDecimal);
    static final ArgumentType<Boolean> BOOLEAN = new TypedArgument<Boolean>(ArgumentSpec.BOOLEAN, TypedArgument::parseBoolean);

    private final ArgumentSpec spec;
    private final Function<String, T> parser;

    TypedArgument(ArgumentSpec spec, Function<String, T> parser) {
        this.spec = spec;
        this.parser = parser;
    }

    @Nullable
    @Override
    public T parse(@NotNull String value) {
        return parser.apply(value);
    }

    @NotNull
    @Override
    public ArgumentSpec getSpec() {
        return spec;
    }

    /** Parses a base-10 integer, or returns {@code null}. */
    @Nullable
    static Integer parseInt(@NotNull String token) {
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a base-10 64-bit integer, or returns {@code null}. */
    @Nullable
    static Long parseLong(@NotNull String token) {
        try {
            return Long.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses an arbitrary-precision decimal, or returns {@code null}. */
    @Nullable
    static BigDecimal parseDecimal(@NotNull String token) {
        try {
            return new BigDecimal(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Checks that a range declared by a factory is ordered. */
    static void requireOrdered(boolean ordered) {
        if (!ordered) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
    }

    static Boolean parseBoolean(String token) {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "true":
            case "yes":
            case "on":
            case "1":
                return Boolean.TRUE;
            case "false":
            case "no":
            case "off":
            case "0":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}
