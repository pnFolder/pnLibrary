package org.pnfolder.pnlibrary.updates.service;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult;

/** What the update orchestrator calls out to: resolving, staging, announcing and observing. */
public final class UpdateOrchestratorHooks {
    /** Receives every plan state the orchestrator publishes or fails to produce. */
    @FunctionalInterface
    public interface StateObserver {
        /** Called with the state, the person or system behind it and the operation name. */
        void observe(@NotNull UpdatePlanSnapshot snapshot, @NotNull UpdateActor actor, @NotNull String operation);
    }

    private final Supplier<ResolutionResult> resolver;
    private final Consumer<UpdatePlanSnapshot> stageAction;
    private final Consumer<UpdatePlanSnapshot> announcement;
    private final Predicate<UpdatePlanSnapshot> automaticAllowed;
    private final Supplier<ResolutionResult> remoteResolver;
    private final StateObserver observeState;
    private final Runnable beforeCheck;
    private final BooleanSupplier automaticOverridesActive;
    private final BiConsumer<String, Throwable> unexpectedFailure;

    private UpdateOrchestratorHooks(Builder builder) {
        this.resolver = builder.resolver;
        this.stageAction = builder.stageAction;
        this.announcement = builder.announcement;
        this.automaticAllowed = builder.automaticAllowed;
        this.remoteResolver = builder.remoteResolver;
        this.observeState = builder.observeState;
        this.beforeCheck = builder.beforeCheck;
        this.automaticOverridesActive = builder.automaticOverridesActive;
        this.unexpectedFailure = builder.unexpectedFailure;
    }

    /**
     * Starts the hooks every orchestrator needs.
     *
     * @param resolver resolves the plan from cached catalogs
     * @param stageAction downloads and prepares a plan
     * @param announcement tells administrators about a plan
     */
    @NotNull
    public static Builder builder(@NotNull Supplier<ResolutionResult> resolver,
                                  @NotNull Consumer<UpdatePlanSnapshot> stageAction,
                                  @NotNull Consumer<UpdatePlanSnapshot> announcement) {
        return new Builder(resolver, stageAction, announcement);
    }

    /** Resolves the plan from cached catalogs. */
    @NotNull
    public Supplier<ResolutionResult> getResolver() {
        return resolver;
    }

    /** Downloads and prepares a plan. */
    @NotNull
    public Consumer<UpdatePlanSnapshot> getStageAction() {
        return stageAction;
    }

    /** Tells administrators about a plan. */
    @NotNull
    public Consumer<UpdatePlanSnapshot> getAnnouncement() {
        return announcement;
    }

    /** Whether a plan may be staged without an administrator. */
    @NotNull
    public Predicate<UpdatePlanSnapshot> getAutomaticAllowed() {
        return automaticAllowed;
    }

    /** Resolves the plan from fresh catalogs; null falls back to {@link #getResolver()}. */
    @Nullable
    public Supplier<ResolutionResult> getRemoteResolver() {
        return remoteResolver;
    }

    /** Receives every published or failed state. */
    @NotNull
    public StateObserver getObserveState() {
        return observeState;
    }

    /** Runs before each check. */
    @NotNull
    public Runnable getBeforeCheck() {
        return beforeCheck;
    }

    /** Whether any administrator override turns automatic download on. */
    @NotNull
    public BooleanSupplier getAutomaticOverridesActive() {
        return automaticOverridesActive;
    }

    /** Receives failures nobody else would see. */
    @NotNull
    public BiConsumer<String, Throwable> getUnexpectedFailure() {
        return unexpectedFailure;
    }

    /** Collects the optional hooks. */
    public static final class Builder {
        private final Supplier<ResolutionResult> resolver;
        private final Consumer<UpdatePlanSnapshot> stageAction;
        private final Consumer<UpdatePlanSnapshot> announcement;
        private Predicate<UpdatePlanSnapshot> automaticAllowed = snapshot -> true;
        private Supplier<ResolutionResult> remoteResolver;
        private StateObserver observeState = (snapshot, actor, operation) -> { };
        private Runnable beforeCheck = () -> { };
        private BooleanSupplier automaticOverridesActive = () -> false;
        private BiConsumer<String, Throwable> unexpectedFailure = (message, error) -> { };

        private Builder(Supplier<ResolutionResult> resolver, Consumer<UpdatePlanSnapshot> stageAction,
                        Consumer<UpdatePlanSnapshot> announcement) {
            this.resolver = resolver;
            this.stageAction = stageAction;
            this.announcement = announcement;
        }

        /** Decides whether a plan may be staged without an administrator; the default allows it. */
        @NotNull
        public Builder automaticAllowed(@NotNull Predicate<UpdatePlanSnapshot> value) {
            this.automaticAllowed = value;
            return this;
        }

        /** Resolves the plan from fresh catalogs when a check asks for it. */
        @NotNull
        public Builder remoteResolver(@NotNull Supplier<ResolutionResult> value) {
            this.remoteResolver = value;
            return this;
        }

        /** Observes every published or failed state. */
        @NotNull
        public Builder observeState(@NotNull StateObserver value) {
            this.observeState = value;
            return this;
        }

        /** Runs before each check. */
        @NotNull
        public Builder beforeCheck(@NotNull Runnable value) {
            this.beforeCheck = value;
            return this;
        }

        /** Tells whether an administrator override turns automatic download on. */
        @NotNull
        public Builder automaticOverridesActive(@NotNull BooleanSupplier value) {
            this.automaticOverridesActive = value;
            return this;
        }

        /** Receives failures of scheduled work nobody else would see. */
        @NotNull
        public Builder unexpectedFailure(@NotNull BiConsumer<String, Throwable> value) {
            this.unexpectedFailure = value;
            return this;
        }

        /** Builds the hooks. */
        @NotNull
        public UpdateOrchestratorHooks build() {
            return new UpdateOrchestratorHooks(this);
        }
    }
}
