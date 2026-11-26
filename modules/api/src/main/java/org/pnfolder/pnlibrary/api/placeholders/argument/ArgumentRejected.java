package org.pnfolder.pnlibrary.api.placeholders.argument;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Thrown by {@link ArgumentInput#fail}; it is the answer "this text does not fit", not an error of the plugin. */
public final class ArgumentRejected extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String reason;

    /** Creates the rejection with the {@code reason} the text does not fit. */
    public ArgumentRejected(@NotNull String reason) {
        super(reason, null, false, false);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Why the text does not fit. */
    @NotNull
    public String getReason() {
        return reason;
    }
}
