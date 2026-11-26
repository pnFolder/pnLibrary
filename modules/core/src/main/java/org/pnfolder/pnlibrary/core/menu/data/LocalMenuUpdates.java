package org.pnfolder.pnlibrary.core.menu.data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;

/** The update facts of this server's library: products, history, backups and the settings an administrator changes. */
abstract class LocalMenuUpdates implements MenuData {
    protected final PnLibrary library;

    LocalMenuUpdates(PnLibrary library) {
        this.library = library;
    }

    @Override
    public @NotNull List<UpdateSnapshot> snapshots() {
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (UpdateRegistration product : library.getUpdates().getProducts().all()) {
            snapshots.add(product.getSnapshot());
        }
        Collections.sort(snapshots, new Comparator<UpdateSnapshot>() {
            @Override
            public int compare(UpdateSnapshot a, UpdateSnapshot b) {
                return a.getProduct().toLowerCase(Locale.ROOT).compareTo(b.getProduct().toLowerCase(Locale.ROOT));
            }
        });
        return snapshots;
    }

    @Override
    public @Nullable UpdateSnapshot snapshot(@NotNull String product) {
        UpdateRegistration found = library.getUpdates().getProducts().get(product);
        return found == null ? null : found.getSnapshot();
    }

    @Override
    public @Nullable PlanInfo plan() {
        return LocalUpdateData.plan(library);
    }

    @Override
    public @Nullable Instant heldUntil(@NotNull String product) {
        return library.getUpdates().getSettings().heldUntil(product);
    }

    @Override
    public @NotNull CompletionStage<Integer> historyCount(@Nullable String product) {
        return library.getUpdates().getHistory().count(product);
    }

    @Override
    public @NotNull CompletionStage<List<UpdateHistoryEntry>> historyPage(int page, @Nullable String product) {
        return library.getUpdates().getHistory().page(page, product);
    }

    @Override
    public @NotNull CompletionStage<List<UpdateBackup>> backups() {
        return library.getUpdates().getBackups().list();
    }

    @Override
    public @NotNull CompletionStage<Boolean> checkAll(@NotNull UpdateActor actor) {
        return LocalUpdateData.checkAll(library, actor);
    }

    @Override
    public void checkOne(@NotNull String product, @NotNull UpdateActor actor) {
        UpdateRegistration found = library.getUpdates().getProducts().get(product);
        if (found != null) {
            found.checkNow();
        }
    }

    @Override
    public @NotNull CompletionStage<Boolean> setAutomatic(@NotNull String product, boolean enabled, @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(library.getUpdates().getSettings().automaticDownload(product, enabled, actor));
    }

    @Override
    public @NotNull CompletionStage<Boolean> setChannel(@NotNull String product, @Nullable UpdateChannel channel, @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(library.getUpdates().getSettings().channel(product, channel, actor));
    }

    @Override
    public @NotNull CompletionStage<Boolean> setJavaBuild(@NotNull String product, @Nullable Integer java, @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(library.getUpdates().getSettings().javaBuild(product, java, actor));
    }

    @Override
    public @NotNull CompletionStage<Boolean> setHeld(@NotNull String product, @Nullable Duration duration, @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(duration == null
            ? library.getUpdates().getSettings().release(product, actor)
            : library.getUpdates().getSettings().hold(product, duration, actor));
    }

    @Override
    public @NotNull CompletionStage<String> extractBackup(@NotNull UUID id, @NotNull String product, @NotNull UpdateActor actor) {
        return library.getUpdates().getBackups().extract(id, product, actor);
    }

    @Override
    public @NotNull CompletionStage<Boolean> restoreDatabase(@NotNull UUID id, @NotNull String product, @NotNull String name,
                                                            @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(library.getUpdates().getBackups().restoreDatabase(id, product, name, actor));
    }

    @Override
    public @NotNull CompletionStage<Boolean> rollback(@NotNull UUID id, boolean restoreConfigurations, @NotNull UpdateActor actor) {
        return LocalUpdateData.yes(library.getUpdates().getBackups().rollback(id, actor, restoreConfigurations));
    }
}
