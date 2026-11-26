package org.pnfolder.pnlibrary.api.scenario.expression;

import org.jetbrains.annotations.NotNull;

/** A mistake in the text of an expression, or a value an expression cannot work with. */
public final class ExpressionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ExpressionException(@NotNull String message) {
        super(message);
    }
}
