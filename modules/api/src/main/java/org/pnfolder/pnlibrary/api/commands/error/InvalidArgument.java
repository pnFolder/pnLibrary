package org.pnfolder.pnlibrary.api.commands.error;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** An argument that does not parse. */
public class InvalidArgument extends BadUsage {
    private final String argument;
    private final String value;

    /**
     * Creates the error.
     *
     * @param usage generated hint, such as {@code /shop buy <item>}
     * @param argument name of the argument
     * @param value what was typed
     */
    public InvalidArgument(@NotNull String usage, @NotNull String argument, @NotNull String value) {
        super(usage);
        this.argument = Objects.requireNonNull(argument, "argument");
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Name of the argument. */
    @NotNull
    public final String getArgument() {
        return argument;
    }

    /** What was typed. */
    @NotNull
    public final String getValue() {
        return value;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        Map<String, Object> values = new LinkedHashMap<>(super.placeholders());
        values.put("argument", argument);
        values.put("value", value);
        return values;
    }
}
