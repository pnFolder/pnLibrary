package org.pnfolder.pnlibrary.bungee.api;

import net.md_5.bungee.api.ProxyServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Native BungeeCord API exposed by pnLibrary on Bungee-compatible proxies. */
@ApiStatus.NonExtendable
public interface BungeePlatform {

    /** Returns the active native proxy; its server and player APIs remain directly available. */
    @NotNull
    ProxyServer getProxy();
}
