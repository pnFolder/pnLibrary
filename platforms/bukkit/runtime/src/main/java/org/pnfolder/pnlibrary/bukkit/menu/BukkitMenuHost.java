package org.pnfolder.pnlibrary.bukkit.menu;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandReplies;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.menu.LibraryMenu;
import org.pnfolder.pnlibrary.core.menu.MenuHost;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.owner.OwnerInfo;
import org.pnfolder.pnlibrary.core.menu.data.remote.RemoteMenuData;

/** What the portable menu needs from a Bukkit server. */
final class BukkitMenuHost implements MenuHost {
    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;
    private final PnLibrary library;
    private final BukkitCommandReplies replies;
    private final Link link;
    private final ServerScheduler scheduler;
    private final ConcurrentHashMap<String, LibraryMenu> remotes = new ConcurrentHashMap<>();

    BukkitMenuHost(Plugin plugin, PnLibrary library, BukkitCommandReplies replies, Link link,
                   ServerScheduler scheduler) {
        this.plugin = plugin;
        this.library = library;
        this.replies = replies;
        this.link = link;
        this.scheduler = scheduler;
    }

    @Override
    public Link getLink() {
        return link;
    }

    @Override
    public void sync(Runnable action) {
        replies.onServerThread(() -> action.run());
    }

    @Override
    public void async(Runnable action) {
        scheduler.async(() -> {
            if (!library.isClosed()) {
                action.run();
            }
        });
    }

    @Override
    public void later(long delayMillis, Runnable action) {
        scheduler.later(Math.max(delayMillis / MILLIS_PER_TICK, 1), () -> {
            if (!library.isClosed()) {
                action.run();
            }
        });
    }

    @Override
    public OwnerInfo owner(Object owner) {
        return owner instanceof Plugin ? info((Plugin) owner) : null;
    }

    @Override
    public OwnerInfo ownerNamed(String name) {
        Plugin found = null;
        try {
            found = plugin.getServer().getPluginManager().getPlugin(name);
        } catch (RuntimeException error) {
            // Fall through to the registered plugins.
        }
        if (found == null) {
            for (PluginRegistration registered : library.getPlugins().all()) {
                Object owner = registered.getOwner();
                if (owner instanceof Plugin && (owner.getClass().getSimpleName().equals(name)
                    || ((Plugin) owner).getName().equalsIgnoreCase(name))) {
                    found = (Plugin) owner;
                    break;
                }
            }
        }
        return found == null ? null : info(found);
    }

    @Override
    public OwnerInfo ownerOf(Class<?> type) {
        ClassLoader loader = type == null ? null : type.getClassLoader();
        if (loader == null) {
            return null;
        }
        for (PluginRegistration registered : library.getPlugins().all()) {
            Object owner = registered.getOwner();
            if (owner instanceof Plugin && owner.getClass().getClassLoader() == loader) {
                return info((Plugin) owner);
            }
        }
        return null;
    }

    @Override
    public boolean openRemote(MenuViewer viewer, String target) {
        if (!link.getEnabled()) {
            return false;
        }
        remotes.computeIfAbsent(target.toLowerCase(java.util.Locale.ROOT),
            key -> new LibraryMenu(new RemoteMenuData(target, link.getData()), this, target)).open(viewer);
        return true;
    }

    private static OwnerInfo info(Plugin plugin) {
        return new OwnerInfo(plugin.getName(), plugin.getDescription().getVersion(),
            Collections.unmodifiableList(plugin.getDescription().getAuthors()));
    }
}
