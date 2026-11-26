package org.pnfolder.pnlibrary.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bukkit.audience.JoinNoticeDelivery;
import org.pnfolder.pnlibrary.bukkit.updates.UpdateReminders;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.events.player.PlayerLifecyclePublisher;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/** Bukkit lifecycle notifications kept separate from portable command registration. */
final class BukkitLifecycleListener implements Listener, AutoCloseable {
    private final Plugin plugin;
    private volatile PnLibrary library;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<Runnable> readyCallbacks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean serverReady = new AtomicBoolean(false);
    private final UpdateReminders reminders;

    BukkitLifecycleListener(Plugin plugin, PnLibrary library) {
        this.plugin = plugin;
        this.library = library;
        this.reminders = new UpdateReminders(plugin, () -> this.library);
    }

    /** Runs a callback immediately when ready, otherwise queues it for server startup. */
    void whenServerReady(Runnable task) {
        if (closed.get()) {
            return;
        }
        if (serverReady.get()) {
            task.run();
            return;
        }
        readyCallbacks.add(task);
        if (closed.get()) {
            readyCallbacks.remove(task);
            return;
        }
        // Readiness may have been published between the first check and the enqueue.
        // Remove and run this callback here so it cannot remain stranded in the queue.
        if (serverReady.get() && readyCallbacks.remove(task)) {
            task.run();
        }
    }

    BukkitLifecycleListener start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reminders.start();
        // ServerLoadEvent exists on modern Bukkit/Paper, but this module also targets old 1.8 APIs.
        // Register it reflectively when available.
        Attempt<Void> registered = Failures.runCatchingNonFatal(() -> {
            Class<? extends Event> eventType = Class.forName("org.bukkit.event.server.ServerLoadEvent").asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(eventType, this, EventPriority.MONITOR,
                (listener, event) -> markServerReady(), plugin);
            return null;
        });
        if (registered.isFailure() && !(registered.exceptionOrNull() instanceof ClassNotFoundException)) {
            // Servers without the event are expected; any other failure leaves readiness callbacks waiting.
            PnLibrary active = library;
            if (active != null) {
                active.getLogging().logger(plugin, "Runtime")
                    .error("Could not listen for server readiness", registered.exceptionOrNull());
            }
        }
        return this;
    }

    /** Announces the portable join event once every other listener has handled the native one. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void publishPlayerJoined(PlayerJoinEvent event) {
        PnLibrary active = library;
        if (active != null) {
            PlayerLifecyclePublisher.joined(active, event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }

    /** Announces the portable quit event while the player is still resolvable. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void publishPlayerLeft(PlayerQuitEvent event) {
        PnLibrary active = library;
        if (active != null) {
            PlayerLifecyclePublisher.left(active, event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }

    /** Tells authorized players about updates and shows the journal notice after joining. */
    @EventHandler
    public void onAdministratorJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean canSeeJournal = player.isOp() || player.hasPermission("pnlibrary.events.view");
        if (reminders.canSeeUpdates(player)) {
            reminders.remind(player, true);
        }
        if (canSeeJournal) {
            showJournalNotice(player);
        }
    }

    private void showJournalNotice(Player player) {
        PnLibrary runtime = library;
        if (runtime == null) {
            return;
        }
        new JoinNoticeDelivery(RuntimeBridge.runtimeTaskScope(runtime)).deliver(
            player,
            () -> {
                PnLibrary active = library;
                return active == null ? new ArrayList<Component>() : RuntimeBridge.journalJoinNotice(active);
            },
            lines -> {
                PnLibrary active = library;
                Audience audience = active == null ? null : active.getAudiences().player(player.getUniqueId());
                if (audience != null) {
                    for (Component line : lines) {
                        audience.sendMessage(line);
                    }
                }
            });
    }

    /** Closing the plugin registration releases all its module commands and task scopes. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        PnLibrary active = library;
        if (active != null) {
            active.getPlugins().unregister(event.getPlugin());
        }
    }

    private void markServerReady() {
        if (!serverReady.compareAndSet(false, true)) {
            return;
        }
        for (Runnable callback : new ArrayList<>(readyCallbacks)) {
            Attempt<Void> result = Failures.runCatchingNonFatal(() -> {
                callback.run();
                return null;
            });
            PnLibrary active = library;
            if (result.isFailure() && active != null) {
                active.getLogging().logger(plugin, "Runtime")
                    .error("A startup task failed after the server became ready", result.exceptionOrNull());
            }
        }
        readyCallbacks.clear();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        HandlerList.unregisterAll(this);
        reminders.close();
        readyCallbacks.clear();
        library = null;
    }
}
