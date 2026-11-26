package org.pnfolder.pnlibrary.bukkit.menu;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandReplies;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.menu.LibraryMenu;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.data.LocalMenuData;
import org.pnfolder.pnlibrary.core.menu.data.MenuDataServer;

/**
 * The inventory menu of {@code /pn gui} on a Bukkit server.
 *
 * <p>The screens themselves are {@link LibraryMenu}, which every platform shares; this class only opens them for a
 * player and lets the menu reach what the server provides.
 */
public final class PluginsMenu implements AutoCloseable {
    private final BukkitCommandReplies replies;
    private final BukkitMenuAdapter adapter;
    private final LibraryMenu menu;
    private ExecutorService menuData;

    public PluginsMenu(Plugin plugin, PnLibrary library, BukkitCommandReplies replies, Link link,
                       ServerScheduler scheduler) {
        this.replies = replies;
        this.adapter = new BukkitMenuAdapter(plugin, library, scheduler);
        BukkitMenuHost host = new BukkitMenuHost(plugin, library, replies, link, scheduler);
        LocalMenuData data = new LocalMenuData(library, host,
            (viewer, line) -> RemoteMenus.run(scheduler, viewer, line), null);
        this.menu = new LibraryMenu(data, host, null);
        // Only a server with a link is asked menu questions by other servers; without one no thread pool is needed.
        if (link.getEnabled()) {
            ExecutorService executor = Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "pnlibrary-menu-data");
                thread.setDaemon(true);
                return thread;
            });
            menuData = executor;
            java.util.function.BiPredicate<String, String> holds = (viewer, permission) -> {
                Player online = Bukkit.getPlayerExact(viewer);
                return online != null && online.hasPermission(permission);
            };
            link.answerCallsWith(new MenuDataServer(data, executor, holds));
        }
    }

    /** Stops the threads that answer menu questions from other servers. */
    @Override
    public void close() {
        if (menuData != null) {
            menuData.shutdown();
        }
    }

    /** Opens the menu for {@code sender}, or explains that the console has no inventory. */
    public void openFor(CommandSender sender, org.pnfolder.pnlibrary.api.commands.CommandSender reply) {
        if (sender instanceof Player) {
            menu.home(adapter.viewer((Player) sender));
        } else {
            replies.say(reply, ChatTone.INFO, "Меню доступно только игроку.", "В консоли используйте /pn updates.");
        }
    }

    /** Opens the values of the choice setting {@code key} for {@code sender}; false when it is not a player. */
    public boolean pickFor(CommandSender sender, String key, String filter) {
        if (!(sender instanceof Player)) {
            return false;
        }
        menu.pickSetting(adapter.viewer((Player) sender), key, filter);
        return true;
    }

    public void home(MenuViewer viewer) {
        menu.home(viewer);
    }
}
