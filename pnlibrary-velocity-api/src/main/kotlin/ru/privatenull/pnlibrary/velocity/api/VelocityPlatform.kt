package ru.privatenull.pnlibrary.velocity.api

import com.velocitypowered.api.proxy.ProxyServer

/** Native Velocity API exposed by pnLibrary on Velocity proxies. */
interface VelocityPlatform {
    /** Active native proxy; its registered servers and players remain directly available. */
    val server: ProxyServer
}
