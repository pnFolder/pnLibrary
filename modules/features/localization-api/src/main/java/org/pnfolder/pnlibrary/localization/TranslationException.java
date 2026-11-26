package org.pnfolder.pnlibrary.localization;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Failure raised while locating, downloading, verifying, or parsing translations. */
public class TranslationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /** Creates a failure of the given category. */
    public TranslationException(@NotNull Reason reason, @NotNull String message) {
        this(reason, message, null);
    }

    /** Creates a failure of the given category that wraps its {@code cause}. */
    public TranslationException(@NotNull Reason reason, @NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Stable failure category suitable for programmatic handling. */
    @NotNull
    public Reason getReason() {
        return reason;
    }

    /** Supported translation failure categories. */
    public enum Reason {
        UNSUPPORTED_VERSION, INVALID_LOCALE, UNAVAILABLE_LOCALE, OFFLINE,
        REMOTE, INTEGRITY, MALFORMED_DATA, CLOSED
    }
}
