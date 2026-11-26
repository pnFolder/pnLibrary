package org.pnfolder.pnlibrary.api.scenario.requirements;

import org.jetbrains.annotations.NotNull;

/**
 * The answer of a {@link Requirement}: {@link Met}, or {@link Unmet} with a reason.
 *
 * <pre>{@code
 * Verdict verdict = requirement.test(context);
 * if (!verdict.isMet()) {
 *     player.sendMessage(Component.text(((Verdict.Unmet) verdict).getReason()));
 * }
 * }</pre>
 */
public interface Verdict {
    /** Whether the requirement is satisfied. */
    default boolean isMet() {
        return this == Met.INSTANCE;
    }

    /** {@link Met} when {@code condition} holds, otherwise {@link Unmet} with {@code reason}. */
    @NotNull
    static Verdict of(boolean condition, @NotNull String reason) {
        return condition ? Met.INSTANCE : new Unmet(reason);
    }

    /** The requirement is satisfied. */
    final class Met implements Verdict {
        /** The only instance. */
        public static final Met INSTANCE = new Met();

        private Met() {
        }

        @NotNull
        @Override
        public String toString() {
            return "Met";
        }
    }

    /** The requirement is not satisfied; the reason is short, readable text. */
    final class Unmet implements Verdict {
        private final String reason;

        /** Creates an answer that says no because of {@code reason}. */
        public Unmet(@NotNull String reason) {
            this.reason = reason;
        }

        /** Short readable text that can be shown to the player. */
        @NotNull
        public String getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Unmet && reason.equals(((Unmet) other).reason);
        }

        @Override
        public int hashCode() {
            return reason.hashCode();
        }

        @NotNull
        @Override
        public String toString() {
            return "Unmet(reason=" + reason + ")";
        }
    }
}
