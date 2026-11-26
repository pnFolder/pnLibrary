package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;

/** The text of one path part together with its {@link ArgumentValue}, kept in the call until the method needs it. */
final class ParsedArgument {
    private final String text;
    private final ArgumentValue<Object> argument;

    ParsedArgument(@NotNull String text, @NotNull ArgumentValue<Object> argument) {
        this.text = text;
        this.argument = argument;
    }

    String getText() {
        return text;
    }

    ArgumentValue<Object> getArgument() {
        return argument;
    }
}
