package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.velocity.api.VelocityPlatform

/** Native-backed Velocity platform API installed by the Velocity runtime. */
internal class VelocityPlatformImpl(
    override val server: ProxyServer,
) : VelocityPlatform
