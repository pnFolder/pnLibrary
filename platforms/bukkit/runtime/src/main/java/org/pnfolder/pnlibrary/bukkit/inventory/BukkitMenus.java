package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Opens, tracks and closes the menus of every plugin; one instance serves the whole library. */
public final class BukkitMenus implements MenuService, AutoCloseable {
    private final Plugin host;
    private final TaskScope tasks;
    private final MenuExecution execution;
    private final MenuInventories inventories;
    private final MenuSessions sessions = new MenuSessions();
    private final MenuListener listener = new MenuListener(sessions);

    public BukkitMenus(Plugin host, TaskScope tasks, Supplier<PnLogger> pnLogger) {
        this(host, tasks, new NativeMenuExecution(host, pnLogger), NativeMenuInventories.INSTANCE);
    }

    BukkitMenus(Plugin host, TaskScope tasks, MenuExecution execution, MenuInventories inventories) {
        this.host = host;
        this.tasks = tasks;
        this.execution = execution;
        this.inventories = inventories;
        host.getServer().getPluginManager().registerEvents(listener, host);
    }

    @NotNull
    @Override
    public MenuSession open(@NotNull Plugin owner, @NotNull Player player, @NotNull Menu menu) {
        execution.requireOwner(player);
        if (sessions.isClosed()) {
            throw new IllegalStateException("Menu service is closed");
        }
        if (!owner.isEnabled()) {
            throw new IllegalStateException("Plugin " + owner.getName() + " is disabled");
        }
        BukkitMenuSession previous = sessions.of(player);
        if (previous != null) {
            previous.end();
        }
        BukkitMenuSession session = createSession(owner, player, menu);
        sessions.add(session);
        try {
            show(session);
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                session.discard();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
        return session;
    }

    @Nullable
    @Override
    public MenuSession session(@NotNull Player player) {
        return sessions.of(player);
    }

    @Override
    public void close(@NotNull Plugin owner) {
        List<Runnable> actions = new ArrayList<>();
        for (BukkitMenuSession session : sessions.of(owner)) {
            actions.add(session::discard);
        }
        ActionsRunner.runEach(actions);
    }

    @Override
    public void close() {
        List<BukkitMenuSession> open = sessions.shutdown();
        if (open == null) {
            return;
        }
        List<Runnable> actions = new ArrayList<>();
        for (BukkitMenuSession session : open) {
            actions.add(session::discard);
        }
        actions.add(tasks::close);
        actions.add(() -> HandlerList.unregisterAll(listener));
        ActionsRunner.runEach(actions);
    }

    private BukkitMenuSession createSession(Plugin owner, Player player, Menu menu) {
        MenuHolder holder = new MenuHolder(UUID.randomUUID());
        org.bukkit.inventory.Inventory inventory = inventories.create(menu, holder);
        holder.bind(inventory);
        for (Map.Entry<Integer, MenuItem> entry : menu.getItems().entrySet()) {
            ItemStackCopy.place(inventory, entry.getKey(), entry.getValue().getItem());
        }
        return new BukkitMenuSession(holder.getId(), owner, player, menu, inventory, execution, tasks, sessions);
    }

    private void show(BukkitMenuSession session) {
        session.getPlayer().openInventory(session.getInventory());
        if (session.isActive() && session.getMenu().getRenderer() != null) {
            session.getMenu().getRenderer().render(session);
        }
        if (session.isActive() && session.getMenu().getOpenHandler() != null) {
            session.getMenu().getOpenHandler().handle(session);
        }
    }
}
