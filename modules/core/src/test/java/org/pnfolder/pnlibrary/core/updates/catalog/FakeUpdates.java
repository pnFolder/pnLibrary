package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.updates.UpdateBackups;
import org.pnfolder.pnlibrary.api.updates.UpdateHistory;
import org.pnfolder.pnlibrary.api.updates.UpdatePlans;
import org.pnfolder.pnlibrary.api.updates.UpdateProducts;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.api.updates.UpdateSettings;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** An update service that remembers what it was asked to change. */
    final class FakeUpdates implements UpdateService {
    final UpdatePlanSnapshot current = new UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE, null,
        Collections.<org.pnfolder.pnlibrary.api.updates.state.BlockedReason>emptyList(), null);
    UUID rolledBack;
    Boolean restoreConfigurations;
    UpdateActor actor;
    String channelProduct;
    UpdateChannel channelSet;
    String automaticProduct;
    Boolean automaticSet;
    String heldProduct;
    Duration heldSet;

    private UpdateSnapshot snapshot() {
        return new UpdateSnapshot("pnlibrary", "1.0.0", null, UpdateChannel.BETA, UpdateState.UP_TO_DATE, 17, 8, false, null, null);
    }

    @Override
    public @NotNull UpdateRegistration register(@NotNull Object owner, @NotNull ProductDescriptor product,
                                                @NotNull PluginUpdateRequest request, @NotNull List<PluginDependency> dependencies) {
        throw new IllegalStateException("not used");
    }

    @Override
    public @NotNull UpdateProducts getProducts() {
        return new UpdateProducts() {
            @Override
            public @NotNull List<UpdateRegistration> all() {
                return Collections.emptyList();
            }

            @Override
            public UpdateRegistration get(@NotNull String product) {
                return null;
            }
        };
    }

    @Override
    public @NotNull UpdatePlans getPlans() {
        return new UpdatePlans() {
            @Override
            public @NotNull CompletionStage<UpdatePlanSnapshot> check(@NotNull UpdateActor who) {
                return CompletableFuture.completedFuture(current);
            }

            @Override
            public @NotNull Optional<UpdatePlanSnapshot> current() {
                return Optional.of(current);
            }

            @Override
            public @NotNull List<UpdatePlanSnapshot> recent() {
                return Collections.emptyList();
            }

            @Override
            public @NotNull CompletionStage<UpdatePlanSnapshot> stage(@NotNull UUID planId, @NotNull UpdateActor who) {
                actor = who;
                return CompletableFuture.completedFuture(current);
            }
        };
    }

    @Override
    public @NotNull UpdateSettings getSettings() {
        return new UpdateSettings() {
            @Override
            public @NotNull CompletionStage<UpdateSnapshot> channel(@NotNull String product, UpdateChannel channel, @NotNull UpdateActor who) {
                channelProduct = product;
                channelSet = channel;
                actor = who;
                return CompletableFuture.completedFuture(snapshot());
            }

            @Override
            public @NotNull CompletionStage<UpdateSnapshot> automaticDownload(@NotNull String product, boolean enabled, @NotNull UpdateActor who) {
                automaticProduct = product;
                automaticSet = enabled;
                return CompletableFuture.completedFuture(snapshot());
            }

            @Override
            public @NotNull CompletionStage<List<UpdateSnapshot>> automaticDownloadForAll(boolean enabled, @NotNull UpdateActor who) {
                return CompletableFuture.completedFuture(Collections.singletonList(snapshot()));
            }

            @Override
            public @NotNull CompletionStage<UpdateSnapshot> hold(@NotNull String product, @NotNull Duration duration, @NotNull UpdateActor who) {
                heldProduct = product;
                heldSet = duration;
                return CompletableFuture.completedFuture(snapshot());
            }

            @Override
            public @NotNull CompletionStage<UpdateSnapshot> release(@NotNull String product, @NotNull UpdateActor who) {
                heldProduct = product;
                heldSet = null;
                return CompletableFuture.completedFuture(snapshot());
            }

            @Override
            public Instant heldUntil(@NotNull String product) {
                return null;
            }

            @Override
            public @NotNull CompletionStage<UpdateSnapshot> javaBuild(@NotNull String product, Integer java, @NotNull UpdateActor who) {
                return CompletableFuture.completedFuture(snapshot());
            }
        };
    }

    @Override
    public @NotNull UpdateHistory getHistory() {
        return new UpdateHistory() {
            @Override
            public @NotNull CompletionStage<List<UpdateHistoryEntry>> page(int page, String product) {
                return CompletableFuture.completedFuture(Collections.<UpdateHistoryEntry>emptyList());
            }

            @Override
            public @NotNull CompletionStage<Integer> count(String product) {
                return CompletableFuture.completedFuture(0);
            }
        };
    }

    @Override
    public @NotNull UpdateBackups getBackups() {
        return new UpdateBackups() {
            @Override
            public @NotNull CompletionStage<List<UpdateBackup>> list() {
                return CompletableFuture.completedFuture(Collections.<UpdateBackup>emptyList());
            }

            @Override
            public @NotNull CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UpdateActor who) {
                return CompletableFuture.completedFuture(current);
            }

            @Override
            public @NotNull CompletionStage<UpdatePlanSnapshot> rollback(@NotNull UUID transactionId, @NotNull UpdateActor who,
                                                                       boolean restore) {
                rolledBack = transactionId;
                restoreConfigurations = restore;
                return CompletableFuture.completedFuture(current);
            }

            @Override
            public @NotNull CompletionStage<Void> delete(@NotNull UUID transactionId, @NotNull UpdateActor who) {
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public @NotNull CompletionStage<Void> restoreDatabase(@NotNull UUID transactionId, @NotNull String product,
                                                                  @NotNull String name, @NotNull UpdateActor who) {
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public @NotNull CompletionStage<String> extract(@NotNull UUID transactionId, @NotNull String product, @NotNull UpdateActor who) {
                return CompletableFuture.completedFuture("");
            }
        };
    }
}
