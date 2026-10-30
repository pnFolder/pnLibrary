package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.menu.LibraryMenu
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.bukkit.command.CommandSender as NativeSender
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The inventory menu of `/pn gui` on a Bukkit server.
 *
 * The screens themselves are [LibraryMenu], which every platform shares; this class only opens them for a player and
 * lets the menu reach what the server provides.
 */
internal class PluginsMenu(
    plugin: Plugin,
    library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private val adapter = BukkitMenuAdapter(plugin, library)
    private val menu = LibraryMenu(library, BukkitMenuHost(plugin, library, replies, adapter))

    /** Opens the menu for [sender], or explains that the console has no inventory. */
    fun openFor(sender: NativeSender, reply: Reply) {
        if (sender is Player) menu.home(adapter.viewer(sender))
        else replies.say(reply, ChatTone.INFO, "Меню доступно только игроку.", "В консоли используйте /pn updates.")
    }

    /** Opens the home page for [viewer], a player here or on another server. */
    fun home(viewer: MenuViewer) = menu.home(viewer)
}
