package org.pnfolder.pnlibrary.updates.engine.resolver;

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
        private final List<BlockedReason> advisories;

        /** @param plan atomic compatible update plan */
        public Ready(@NotNull UpdatePlan plan) {
            this(plan, Collections.emptyList());
        }

        /**
         * @param plan atomic compatible update plan
         * @param advisories notes that do not block the plan (see {@link BlockedReason#isAdvisory()})
         */
        public Ready(@NotNull UpdatePlan plan, @NotNull List<BlockedReason> advisories) {
            this.plan = plan;
            this.advisories = Collections.unmodifiableList(new ArrayList<>(advisories));
        }

        /** Notes about what the plan leaves out; the plan is installable regardless. */
        @NotNull
        public List<BlockedReason> getAdvisories() {
            return advisories;
        }

        @NotNull
        public UpdatePlan getPlan() {
            return plan;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Ready)) {
                return false;
            }
            Ready that = (Ready) other;
            return plan.equals(that.plan) && advisories.equals(that.advisories);
        }

        @Override
        public int hashCode() {
            return 31 * plan.hashCode() + advisories.hashCode();
        }

        @Override
        public String toString() {
            return "Ready(plan=" + plan + ", advisories=" + advisories + ")";
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
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
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
