package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.bukkit.server.PnBukkit

/**
 * Opens reusable [Menu] definitions and tracks one active session per player.
 *
 * Opening a new menu replaces the player's previous pnLibrary session. Registrations
 * are owned by the plugin supplied to [open] and are closed automatically when that
 * plugin is disabled.
 */
interface MenuService {
    /**
     * Creates and opens a fresh session for [player].
     *
     * Static items are copied first, followed by rendering and the open callback.
     *
     * @throws IllegalStateException when the service or [owner] is disabled
     */
    fun open(owner: Plugin, player: Player, menu: Menu): MenuSession
    /** Returns the player's active pnLibrary menu session, or `null`. */
    fun session(player: Player): MenuSession?
    /** Closes every session owned by [owner] without invoking plugin close callbacks. */
    fun close(owner: Plugin)
}

/** Entry point for obtaining the installed Bukkit menu service. */
object PnMenus {
    /** Returns the service installed by the active pnLibrary Bukkit runtime. */
    @JvmStatic fun get(): MenuService = PnBukkit.menus()
}
