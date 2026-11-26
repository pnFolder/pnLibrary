package org.pnfolder.pnlibrary.bukkit.inventory;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens reusable {@link Menu} definitions and tracks one active session per player.
 *
 * <p>Opening a new menu replaces the player's previous pnLibrary session. Registrations are owned by the plugin
 * supplied to {@link #open} and are closed automatically when that plugin is disabled.
 * Opening, rendering, and inventory reads/writes require the player's owning thread (the main thread on Bukkit,
 * the entity's region on Folia). Bulk close is safe from any thread and schedules native view cleanup on each
 * player; cleanup requires a live native scheduler.
 */
public interface MenuService {

    /**
     * Creates and opens a fresh session for {@code player}.
     *
     * <p>Static items are copied first, followed by rendering and the open callback.
     *
     * @throws IllegalStateException when the service or {@code owner} is disabled
     */
    @NotNull MenuSession open(@NotNull Plugin owner, @NotNull Player player, @NotNull Menu menu);

    /** Returns the menu {@code player} has open through this service, or {@code null} when there is none. */
    @Nullable MenuSession session(@NotNull Player player);

    /** Closes every session owned by {@code owner} without invoking plugin close callbacks. */
    void close(@NotNull Plugin owner);
}
