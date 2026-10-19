package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.ProxyServer
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform

/** Native-backed Bungee platform API installed by the Bungee runtime. */
internal class BungeePlatformImpl(
    override val proxy: ProxyServer,
) : BungeePlatform
