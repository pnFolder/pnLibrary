package org.pnfolder.pnlibrary.bukkit.inventory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

/** One player's open menu: it ends once, either by the player, the owner or the service. */
final class BukkitMenuSession implements MenuSession {
    private final UUID id;
    private final Plugin owner;
    private final Player player;
    private final Menu menu;
    private final Inventory inventory;
    private final MenuExecution execution;
    private final TaskScope tasks;
    private final MenuSessions sessions;
    private final AtomicBoolean ended = new AtomicBoolean(false);

    BukkitMenuSession(UUID id, Plugin owner, Player player, Menu menu, Inventory inventory, MenuExecution execution,
                      TaskScope tasks, MenuSessions sessions) {
        this.id = id;
        this.owner = owner;
        this.player = player;
        this.menu = menu;
        this.inventory = inventory;
        this.execution = execution;
        this.tasks = tasks;
        this.sessions = sessions;
    }

    UUID getId() {
        return id;
    }

    Plugin getOwner() {
        return owner;
    }

    boolean isActive() {
        return !ended.get();
    }

    @NotNull
    @Override
    public Player getPlayer() {
        return player;
    }

    @NotNull
    @Override
    public Menu getMenu() {
        return menu;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void set(int slot, @Nullable ItemStack item) {
        requireUsable();
        if (slot < 0 || slot >= inventory.getSize()) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        inventory.setItem(slot, item);
    }

    @Nullable
    @Override
    public ItemStack get(int slot) {
        requireUsable();
        return inventory.getItem(slot);
    }

    @Override
    public void refresh() {
        execution.requireOwner(player);
        if (isActive() && menu.getRenderer() != null) {
            menu.getRenderer().render(this);
        }
    }

    @Override
    public void refreshAfter(@NotNull Duration delay) {
        if (!isActive()) {
            return;
        }
        tasks.schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(delay)
                .action(context -> refresh())
                .build());
    }

    @Override
    public void close() {
        if (!isActive()) {
            return;
        }
        // A disabled owner can no longer run scheduled native work, so only tracking is dropped.
        if (!owner.isEnabled()) {
            end();
            return;
        }
        execution.execute(player, () -> {
            if (isActive()) {
                try {
                    closeViewIfShown();
                } finally {
                    endAndNotify();
                }
            }
        });
    }

    boolean isShownIn(Inventory shown) {
        InventoryHolder holder = shown.getHolder();
        return holder instanceof MenuHolder && shown == inventory && ((MenuHolder) holder).getId().equals(id);
    }

    boolean end() {
        if (!ended.compareAndSet(false, true)) {
            return false;
        }
        sessions.remove(this);
        return true;
    }

    void endAndNotify() {
        if (end() && owner.isEnabled() && !sessions.isClosed() && menu.getCloseHandler() != null) {
            menu.getCloseHandler().handle(new MenuClose(this));
        }
    }

    void discard() {
        if (end()) {
            execution.execute(player, this::closeViewIfShown);
        }
    }

    private void closeViewIfShown() {
        if (isShownIn(player.getOpenInventory().getTopInventory())) {
            player.closeInventory();
        }
    }

    private void requireUsable() {
        execution.requireOwner(player);
        if (!isActive()) {
            throw new IllegalStateException("Menu session is closed");
        }
    }
}
