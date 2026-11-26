package org.pnfolder.pnlibrary.updates.service.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** A text that is equal for two plan states an administrator would see as the same news. */
final class PlanFingerprint {
    private PlanFingerprint() {
    }

    /** Builds the fingerprint of a state, its plan and its blockers. */
    @NotNull
    static String of(@NotNull UpdateState state, @Nullable UpdatePlan plan, @NotNull List<?> blockers) {
        StringBuilder text = new StringBuilder();
        text.append(state.name()).append('|').append(plan == null ? null : (Object) plan.getTargetApi()).append('|');
        if (plan != null) {
            List<ProductChange> changes = new ArrayList<>(plan.getChanges());
            Collections.sort(changes, (left, right) -> left.getProduct().getValue().compareTo(right.getProduct().getValue()));
            for (ProductChange change : changes) {
                text.append(change.getProduct().getValue()).append(':').append(change.getFrom()).append('>')
                        .append(change.getTo()).append(';');
            }
        }
        for (Object blocker : blockers) {
            text.append(blocker).append(';');
        }
        return text.toString();
    }

    /** The message of the deepest cause, or the class name when it has none. */
    @NotNull
    static String rootMessage(@NotNull Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : error.getClass().getSimpleName();
    }
}
