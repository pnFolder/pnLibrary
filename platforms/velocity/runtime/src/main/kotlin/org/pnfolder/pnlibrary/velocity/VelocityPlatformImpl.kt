package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform

/** Native-backed Velocity platform API installed by the Velocity runtime. */
internal class VelocityPlatformImpl(
    override val proxy: ProxyServer,
) : VelocityPlatform
