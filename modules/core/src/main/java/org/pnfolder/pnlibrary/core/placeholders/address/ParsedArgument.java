package org.pnfolder.pnlibrary.core.placeholders.address;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;

/** The text of one path part together with its {@link ArgumentValue}, kept in the call until the method needs it. */
public final class ParsedArgument {
    private final String text;
    private final ArgumentValue<Object> argument;

    ParsedArgument(@NotNull String text, @NotNull ArgumentValue<Object> argument) {
        this.text = Objects.requireNonNull(text, "text");
        this.argument = Objects.requireNonNull(argument, "argument");
    }

    String getText() {
        return text;
    }

    public ArgumentValue<Object> getArgument() {
        return argument;
    }
}
