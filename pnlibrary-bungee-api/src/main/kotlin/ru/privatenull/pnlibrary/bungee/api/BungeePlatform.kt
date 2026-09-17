package ru.privatenull.pnlibrary.bungee.api

import net.md_5.bungee.api.ProxyServer

/** Native BungeeCord API exposed by pnLibrary on Bungee-compatible proxies. */
interface BungeePlatform {
    /** Active native proxy; its server and player APIs remain directly available. */
    val proxy: ProxyServer
}
