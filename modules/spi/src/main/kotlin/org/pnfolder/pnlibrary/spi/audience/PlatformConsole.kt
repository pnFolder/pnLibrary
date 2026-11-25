package org.pnfolder.pnlibrary.spi.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.pnfolder.pnlibrary.api.audience.Console

/** The console of a platform: [audience], the native console as an Adventure audience, seen as a [Console]. */
class PlatformConsole(private val audience: Audience) : Console, ForwardingAudience.Single {
    override fun audience(): Audience = audience
}
