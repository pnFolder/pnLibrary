package org.pnfolder.pnlibrary.api.events;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event whose listeners set a typed {@link Result} that the caller reads after dispatch.
 *
 * <p>Each event implements {@link #getResult()} and {@link #setResult} and sets its starting value, which is used
 * until a listener replaces it:
 *
 * <pre>{@code
 * class PurchaseEvent extends ResultedEvent<ResultedEvent.ComponentResult> {
 *     private ComponentResult result = ComponentResult.allowed();
 *     public ComponentResult getResult() { return result; }
 *     public void setResult(ComponentResult result) { this.result = result; }
 * }
 * }</pre>
 *
 * @param <R> result type carried by this event
 */
public abstract class ResultedEvent<R extends ResultedEvent.Result> extends Event {
    /** Current outcome. The final value is available as soon as {@link Event#callEvent()} returns. */
    @NotNull
    public abstract R getResult();

    /** Replaces the outcome; listeners call it to allow, deny or answer. */
    public abstract void setResult(@NotNull R result);

    /**
     * Calls every matching listener, then returns the typed {@link #getResult() result} they left. Use it instead of
     * {@link Event#callEvent()} when the outcome carries more than allowed or denied.
     */
    @NotNull
    public final R callResult() {
        callEvent();
        return getResult();
    }

    /** Outcome that listeners leave on a {@link ResultedEvent}. Implement it to carry extra data. */
    public interface Result {
        /** Whether the event may proceed after dispatch. */
        boolean isAllowed();
    }

    /** Plain allow/deny outcome without a reason. */
    public static final class GenericResult implements Result {
        private static final GenericResult ALLOWED = new GenericResult(true);
        private static final GenericResult DENIED = new GenericResult(false);

        private final boolean allowed;

        private GenericResult(boolean allowed) {
            this.allowed = allowed;
        }

        /** Returns the shared allowed result. */
        @NotNull
        public static GenericResult allowed() {
            return ALLOWED;
        }

        /** Returns the shared denied result. */
        @NotNull
        public static GenericResult denied() {
            return DENIED;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /** Renders as {@code allowed} or {@code denied}. */
        @Override
        public String toString() {
            return allowed ? "allowed" : "denied";
        }
    }

    /**
     * Allow/deny outcome with an optional denial reason rendered as an Adventure {@link Component}.
     *
     * <p>Use {@link #allowed()} or {@link #denied(Component)}, so messages can be styled and localized the same way as
     * the rest of the library.
     */
    public static final class ComponentResult implements Result {
        private static final ComponentResult ALLOWED = new ComponentResult(true, null);

        private final boolean allowed;
        private final Component reason;

        private ComponentResult(boolean allowed, Component reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        /** Returns the shared allowed result that carries no reason. */
        @NotNull
        public static ComponentResult allowed() {
            return ALLOWED;
        }

        /** Returns a denied result that explains why. */
        @NotNull
        public static ComponentResult denied(@NotNull Component reason) {
            return new ComponentResult(false, reason);
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /** Denial reason, or {@code null} when none was given or the result is allowed. */
        @Nullable
        public Component getReason() {
            return reason;
        }

        /** The denial reason as an {@link Optional}, empty when none was given. */
        @NotNull
        public Optional<Component> getReasonComponent() {
            return Optional.ofNullable(reason);
        }

        /** Renders as {@code allowed} or {@code denied}. */
        @Override
        public String toString() {
            return allowed ? "allowed" : "denied";
        }
    }
}
