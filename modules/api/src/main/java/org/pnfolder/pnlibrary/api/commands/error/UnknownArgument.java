package org.pnfolder.pnlibrary.api.commands.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** A word the command does not know, or one too many. */
public class UnknownArgument extends BadUsage {
    private final String value;

    /**
     * Creates the error.
     *
     * @param usage generated hint, such as {@code /shop buy <item>}
     * @param value the unexpected word
     */
    public UnknownArgument(@NotNull String usage, @NotNull String value) {
        super(usage);
        this.value = value;
    }

    /** The unexpected word. */
    @NotNull
    public final String getValue() {
        return value;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        Map<String, Object> values = new LinkedHashMap<String, Object>(super.placeholders());
        values.put("value", value);
        return values;
    }
}
