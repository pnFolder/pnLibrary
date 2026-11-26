package org.pnfolder.pnlibrary.core.menu.data.remote;

import org.pnfolder.pnlibrary.core.menu.data.PluginRow;
import org.pnfolder.pnlibrary.core.menu.data.PlanInfo;
import org.pnfolder.pnlibrary.core.menu.data.MenuCalls;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.core.link.remote.DataClient;

/**
 * The library of another server, reached over the link.
 *
 * <p>The questions go to the target (a game server or the proxy) and the answers come back as JSON. They are waited
 * for, so the menu calls this away from the server thread. Read answers are remembered for a few seconds, because one
 * screen asks for the same facts several times.
 */
public final class RemoteMenuData extends RemoteSystemData {
    /**
     * @param target the server asked, as the proxy knows it
     * @param client where the questions go
     */
    public RemoteMenuData(@NotNull String target, @NotNull DataClient client) {
        super(target, client);
    }

    @Override
    public @NotNull String version() {
        return ask(MenuCalls.VERSION, String.class);
    }

    @Override
    public @NotNull List<UpdateSnapshot> snapshots() {
        return ask(MenuCalls.SNAPSHOTS, MenuWire.listOf(UpdateSnapshot.class));
    }

    @Override
    public @Nullable UpdateSnapshot snapshot(@NotNull String product) {
        return ask(MenuCalls.SNAPSHOT, UpdateSnapshot.class, args("product", product));
    }

    @Override
    public @NotNull List<PluginRow> plugins() {
        return ask(MenuCalls.PLUGINS, MenuWire.listOf(PluginRow.class));
    }

    @Override
    public @Nullable PlanInfo plan() {
        return ask(MenuCalls.PLAN, PlanInfo.class);
    }

    @Override
    public @Nullable Instant heldUntil(@NotNull String product) {
        return ask(MenuCalls.HELD_UNTIL, Instant.class, args("product", product));
    }

    @Override
    public @NotNull CompletionStage<Integer> historyCount(@Nullable String product) {
        return later(() -> this.<Integer>ask(MenuCalls.HISTORY_COUNT, Integer.class, args("product", product)));
    }

    @Override
    public @NotNull CompletionStage<List<UpdateHistoryEntry>> historyPage(int page, @Nullable String product) {
        return later(() -> this.<List<UpdateHistoryEntry>>ask(MenuCalls.HISTORY_PAGE,
            MenuWire.listOf(UpdateHistoryEntry.class), args("page", page, "product", product)));
    }

    @Override
    public @NotNull CompletionStage<List<UpdateBackup>> backups() {
        return later(() -> this.<List<UpdateBackup>>ask(MenuCalls.BACKUPS, MenuWire.listOf(UpdateBackup.class)));
    }

    @Override
    public @NotNull CompletionStage<Boolean> checkAll(@NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.CHECK_ALL, Boolean.class, args(), actor.getName()));
    }

    @Override
    public void checkOne(@NotNull String product, @NotNull UpdateActor actor) {
        ask(MenuCalls.CHECK_ONE, Boolean.class, args("product", product), actor.getName());
    }

    @Override
    public @NotNull CompletionStage<Boolean> setChannel(@NotNull String product, @Nullable UpdateChannel channel, @NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.SET_CHANNEL, Boolean.class,
            args("product", product, "channel", channel == null ? null : channel.name()), actor.getName()));
    }

    @Override
    public @NotNull CompletionStage<Boolean> setJavaBuild(@NotNull String product, @Nullable Integer java, @NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.SET_JAVA_BUILD, Boolean.class,
            args("product", product, "java", java), actor.getName()));
    }

    @Override
    public @NotNull CompletionStage<Boolean> setHeld(@NotNull String product, @Nullable Duration duration, @NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.SET_HELD, Boolean.class,
            args("product", product, "millis", duration == null ? null : duration.toMillis()), actor.getName()));
    }

    @Override
    public @NotNull CompletionStage<String> extractBackup(@NotNull UUID id, @NotNull String product, @NotNull UpdateActor actor) {
        return later(() -> this.<String>ask(MenuCalls.EXTRACT_BACKUP, String.class,
            args("id", id.toString(), "product", product), actor.getName()));
    }

    @Override
    public @NotNull CompletionStage<Boolean> restoreDatabase(@NotNull UUID id, @NotNull String product, @NotNull String name,
                                                            @NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.RESTORE_DATABASE, Boolean.class,
            args("id", id.toString(), "product", product, "name", name), actor.getName()));
    }

    @Override
    public @NotNull CompletionStage<Boolean> rollback(@NotNull UUID id, boolean restoreConfigurations, @NotNull UpdateActor actor) {
        return later(() -> this.<Boolean>ask(MenuCalls.ROLLBACK, Boolean.class,
            args("id", id.toString(), "restore", restoreConfigurations), actor.getName()));
    }
}
