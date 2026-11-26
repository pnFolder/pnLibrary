package org.pnfolder.pnlibrary.bukkit.api;

import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.bukkit.entity.VisualService;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService;
import org.pnfolder.pnlibrary.bukkit.item.ItemService;
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo;

/**
 * Bukkit-only services of pnLibrary. Obtain it with {@code PlatformProvider.of(library, BukkitPlatform.class)}.
 *
 * <p>This module holds contracts only; the implementations live in the Bukkit runtime and are installed by it.
 */
public interface BukkitPlatform {

    /** The running Bukkit server. */
    @NotNull Server getServer();

    /** Name and version of the server software, resolved once at startup. */
    @NotNull ServerInfo getServerInfo();

    /** Opens and tracks inventory menus. */
    @NotNull MenuService getMenus();

    /** Creates, reads and writes items. */
    @NotNull ItemService getItems();

    /** Spawns floating items, blocks and text. */
    @NotNull VisualService getVisuals();
}
