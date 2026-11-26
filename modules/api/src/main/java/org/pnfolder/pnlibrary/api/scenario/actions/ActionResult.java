package org.pnfolder.pnlibrary.api.scenario.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * How one {@link Action} went, and whether the rest of the list should run. It is exactly one of {@link Done},
 * {@link Stopped} or {@link Failed}.
 *
 * <pre>{@code
 * ActionResult result = actions.execute(context);
 * if (result instanceof ActionResult.Failed) {
 *     logger.warning(((ActionResult.Failed) result).getMessage());
 * }
 * }</pre>
 */
public interface ActionResult {
    /** The step finished; the next one runs. */
    final class Done implements ActionResult {
        /** The only instance. */
        public static final Done INSTANCE = new Done();

        private Done() {
        }

        @NotNull
        @Override
        public String toString() {
            return "Done";
        }
    }

    /** The scenario ends on purpose, for example because a requirement was not met. Nothing is undone. */
    final class Stopped implements ActionResult {
        private final String reason;

        /** Creates a stop without a reason. */
        public Stopped() {
            this(null);
        }

        /** Creates a stop; {@code reason} is a short readable cause, or {@code null}. */
        public Stopped(@Nullable String reason) {
            this.reason = reason;
        }

        /** Short readable cause, or {@code null}. */
        @Nullable
        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stopped && Objects.equals(reason, ((Stopped) other).reason);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(reason);
        }

        @NotNull
        @Override
        public String toString() {
            return "Stopped(reason=" + reason + ")";
        }
    }

    /**
     * The step could not do its job. The scenario ends and steps that registered an undo, such as a charge, give
     * back what they took (see {@link ActionContext#onUndo}).
     */
    final class Failed implements ActionResult {
        private final String code;
        private final String message;
        private final Throwable cause;

        /** Creates a failure without an original exception. */
        public Failed(@NotNull String code, @NotNull String message) {
            this(code, message, null);
        }

        /**
         * Creates a failure.
         *
         * @param code stable machine-readable cause such as {@code currency.insufficient-funds}
         * @param message readable cause for logs and players
         * @param cause original exception, when there was one
         */
        public Failed(@NotNull String code, @NotNull String message, @Nullable Throwable cause) {
            this.code = code;
            this.message = message;
            this.cause = cause;
        }

        /** Stable machine-readable cause. */
        @NotNull
        public String getCode() {
            return code;
        }

        /** Readable cause for logs and players. */
        @NotNull
        public String getMessage() {
            return message;
        }

        /** Original exception, or {@code null}. */
        @Nullable
        public Throwable getCause() {
            return cause;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Failed)) {
                return false;
            }
            Failed that = (Failed) other;
            return code.equals(that.code) && message.equals(that.message) && Objects.equals(cause, that.cause);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, message, cause);
        }

        @NotNull
        @Override
        public String toString() {
            return "Failed(code=" + code + ", message=" + message + ", cause=" + cause + ")";
        }
    }
}
