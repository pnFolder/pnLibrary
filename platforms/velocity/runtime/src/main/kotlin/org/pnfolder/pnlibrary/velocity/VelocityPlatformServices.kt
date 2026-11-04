package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform

/** Native-backed Velocity platform API installed by the Velocity runtime. */
internal class VelocityPlatformServices(
    override val proxy: ProxyServer,
) : VelocityPlatform
