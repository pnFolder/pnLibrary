package org.pnfolder.pnlibrary.bukkit.updates;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitTitle;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;

/**
 * Tells administrators in chat about updates: once when they join and again every
 * {@link UpdateDefaults#REMINDER_INTERVAL} while an update is available or waits for a restart. It reads the state
 * the library already knows, so it never asks GitHub, and it stops by itself when the release is gone. Neither the
 * reminder nor its interval can be turned off by an administrator.
 */
public final class UpdateReminders implements AutoCloseable {
    private final Plugin plugin;
    private final Supplier<PnLibrary> library;
    private TaskHandle timer;

    public UpdateReminders(Plugin plugin, Supplier<PnLibrary> library) {
        this.plugin = plugin;
        this.library = library;
    }

    /** Begins the periodic reminder for administrators. */
    public UpdateReminders start() {
        PnLibrary active = library.get();
        if (active == null) {
            return this;
        }
        timer = RuntimeBridge.runtimeTaskScope(active).schedule(
            TaskSpec.builder()
                .name("update reminders")
                .description("Напоминание администраторам об обновлениях.")
                .cancellable(false)
                .runnableNow(false)
                .execution(TaskExecution.global())
                .delay(UpdateDefaults.REMINDER_INTERVAL)
                .interval(UpdateDefaults.REMINDER_INTERVAL)
                .action(context -> {
                    for (Player player : new ArrayList<Player>(plugin.getServer().getOnlinePlayers())) {
                        if (canSeeUpdates(player)) {
                            remind(player, false);
                        }
                    }
                })
                .build());
        return this;
    }

    /** Whether {@code player} is an operator or holds a permission that allows update notices. */
    public boolean canSeeUpdates(Player player) {
        return player.isOp() || player.hasPermission("*") || player.hasPermission("pnlibrary.admin")
            || player.hasPermission("pnlibrary.updates.notify");
    }

    /** Sends the update notice to {@code player} on the player's own thread; a title is shown only on join. */
    public void remind(Player player, boolean withTitle) {
        PnLibrary active = library.get();
        if (active == null) {
            return;
        }
        RuntimeBridge.runtimeTaskScope(active).schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(Duration.ofMillis(50))
                .action(context -> send(active, player, withTitle))
                .build());
    }

    @SuppressWarnings("deprecation")
    private void send(PnLibrary active, Player player, boolean withTitle) {
        if (!player.isOnline()) {
            return;
        }
        Audience audience = active.getAudiences().player(player.getUniqueId());
        if (audience == null) {
            return;
        }
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (UpdateRegistration registration : active.getUpdates().getProducts().all()) {
            snapshots.add(registration.getSnapshot());
        }
        List<Component> notice = UpdateViews.announcement(snapshots, BukkitControlCommand.ROUTES,
            PnLibraryIdentity.SUPPORT_URL);
        if (notice.isEmpty()) {
            return;
        }
        for (Component line : notice) {
            audience.sendMessage(line);
        }
        if (!withTitle) {
            return;
        }
        BukkitTitle title = BukkitTitle.updatesPending(pendingCount(snapshots));
        player.sendTitle(title.getTitle(), title.getSubtitle());
    }

    private static int pendingCount(List<UpdateSnapshot> snapshots) {
        int pending = 0;
        for (UpdateSnapshot snapshot : snapshots) {
            if (snapshot.getState() == UpdateState.UPDATE_AVAILABLE || snapshot.getState() == UpdateState.UPDATE_STAGED) {
                pending++;
            }
        }
        return pending;
    }

    @Override
    public void close() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
