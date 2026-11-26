package org.pnfolder.pnlibrary.bukkit;

import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform;
import org.pnfolder.pnlibrary.bukkit.entity.ArmorStandVisuals;
import org.pnfolder.pnlibrary.bukkit.entity.VisualService;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService;
import org.pnfolder.pnlibrary.bukkit.item.BukkitItems;
import org.pnfolder.pnlibrary.bukkit.item.ItemService;
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo;

/** Native-backed Bukkit platform API installed by the Bukkit runtime. */
final class BukkitPlatformServices implements BukkitPlatform {
    private final Server server;
    private final ServerInfo serverInfo;
    private final MenuService menus;

    BukkitPlatformServices(Server server, ServerInfo serverInfo, MenuService menus) {
        this.server = server;
        this.serverInfo = serverInfo;
        this.menus = menus;
    }

    @NotNull
    @Override
    public Server getServer() {
        return server;
    }

    @NotNull
    @Override
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    @NotNull
    @Override
    public MenuService getMenus() {
        return menus;
    }

    @NotNull
    @Override
    public ItemService getItems() {
        return BukkitItems.INSTANCE;
    }

    @NotNull
    @Override
    public VisualService getVisuals() {
        return ArmorStandVisuals.INSTANCE;
    }
}
