package org.pnfolder.pnlibrary.updates.engine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Result of resolving all managed components as one compatible ecosystem: {@link Ready} or {@link Blocked}. */
public abstract class ResolutionResult {
    private ResolutionResult() {
    }

    /** Complete compatible resolution. */
    public static final class Ready extends ResolutionResult {
        private final UpdatePlan plan;

        /** @param plan atomic compatible update plan */
        public Ready(@NotNull UpdatePlan plan) {
            this.plan = plan;
        }

        @NotNull
        public UpdatePlan getPlan() {
            return plan;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Ready && plan.equals(((Ready) other).plan);
        }

        @Override
        public int hashCode() {
            return plan.hashCode();
        }

        @Override
        public String toString() {
            return "Ready(plan=" + plan + ")";
        }
    }

    /** Resolution that encountered blocking reasons. */
    public static final class Blocked extends ResolutionResult {
        private final List<BlockedReason> reasons;
        private final UpdatePlan fallbackPlan;

        /**
         * @param reasons structured blockers
         * @param fallbackPlan best compatible lower-risk plan, when one exists
         */
        public Blocked(@NotNull List<BlockedReason> reasons, @Nullable UpdatePlan fallbackPlan) {
            this.reasons = Collections.unmodifiableList(new ArrayList<BlockedReason>(reasons));
            this.fallbackPlan = fallbackPlan;
        }

        @NotNull
        public List<BlockedReason> getReasons() {
            return reasons;
        }

        @Nullable
        public UpdatePlan getFallbackPlan() {
            return fallbackPlan;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Blocked)) {
                return false;
            }
            Blocked that = (Blocked) other;
            return reasons.equals(that.reasons) && Objects.equals(fallbackPlan, that.fallbackPlan);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reasons, fallbackPlan);
        }

        @Override
        public String toString() {
            return "Blocked(reasons=" + reasons + ", fallbackPlan=" + fallbackPlan + ")";
        }
    }
}
