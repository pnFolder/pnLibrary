package org.pnfolder.pnlibrary.api.updates.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;

/** Immutable observable graph-plan state. */
public final class UpdatePlanSnapshot {
    private final UUID id;
    private final long revision;
    private final UpdateState state;
    private final UpdatePlan plan;
    private final List<BlockedReason> blockers;
    private final String message;

    /**
     * Creates the snapshot; the plan and its nested lists are copied so later changes cannot reach it.
     *
     * @param id stable plan identity used for stage and confirmation operations
     * @param revision monotonic state revision
     * @param state current graph-update phase
     * @param plan selected atomic update plan, when resolution succeeded
     * @param blockers reasons preventing a complete compatible plan
     * @param message optional diagnostic or user-facing state detail
     */
    public UpdatePlanSnapshot(@NotNull UUID id, long revision, @NotNull UpdateState state, @Nullable UpdatePlan plan,
                              @NotNull List<BlockedReason> blockers, @Nullable String message) {
        if (revision < 0) {
            throw new IllegalArgumentException("Plan revision must not be negative");
        }
        this.id = id;
        this.revision = revision;
        this.state = state;
        this.plan = plan == null ? null : detached(plan);
        this.blockers = UpdateValidation.frozen(blockers);
        this.message = message;
    }

    private static UpdatePlan detached(UpdatePlan plan) {
        List<ProductRelease> selected = new ArrayList<>();
        for (ProductRelease release : plan.getSelected()) {
            selected.add(new ProductRelease(release.getProduct(), release.getVersion(), release.getChannel(),
                release.getSupportedApi(), release.getProvidesApi(),
                UpdateValidation.frozen(release.getDependencies()), release.getRepository(),
                UpdateValidation.frozen(release.getArtifacts()),
                UpdateValidation.frozen(release.getExternalPluginDependencies()), release.getPublishedAt(),
                release.getChangelogUrl(), release.getDescription()));
        }
        return new UpdatePlan(plan.getTargetApi(), UpdateValidation.frozen(plan.getChanges()),
            UpdateValidation.frozen(selected));
    }

    /** Stable plan identity used for stage and confirmation operations. */
    @NotNull
    public UUID getId() {
        return id;
    }

    /** Monotonic state revision. */
    public long getRevision() {
        return revision;
    }

    /** Current graph-update phase. */
    @NotNull
    public UpdateState getState() {
        return state;
    }

    /** Selected atomic update plan, when resolution succeeded; a detached graph with immutable lists. */
    @Nullable
    public UpdatePlan getPlan() {
        return plan;
    }

    /** Immutable reasons preventing a complete compatible plan. */
    @NotNull
    public List<BlockedReason> getBlockers() {
        return blockers;
    }

    /** Optional diagnostic or user-facing state detail. */
    @Nullable
    public String getMessage() {
        return message;
    }
}
