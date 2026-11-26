package org.pnfolder.pnlibrary.bukkit.inventory;

import java.time.Duration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One player's live view of a reusable {@link Menu} definition. */
public interface MenuSession extends AutoCloseable {

    /** The viewer. */
    @NotNull Player getPlayer();

    /** The definition this session shows. */
    @NotNull Menu getMenu();

    /** The open Bukkit inventory. */
    @NotNull Inventory getInventory();

    /** Replaces {@code slot} with {@code item}; {@code null} clears it. */
    void set(int slot, @Nullable ItemStack item);

    /** Returns the item in {@code slot}, or {@code null} when it is empty. */
    @Nullable ItemStack get(int slot);

    /** Invokes {@link Menu#getRenderer()} immediately unless the session has finished. */
    void refresh();

    /** Schedules {@link #refresh()} through the player-aware task scheduler after {@code delay}. */
    void refreshAfter(@NotNull Duration delay);

    /** Closes this session. Safe to repeat. */
    @Override
    void close();
}
