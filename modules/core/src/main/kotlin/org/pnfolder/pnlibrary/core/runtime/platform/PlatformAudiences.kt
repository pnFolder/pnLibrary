package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.audience.Audiences
import org.pnfolder.pnlibrary.api.audience.Console
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Resolves consoles, players and composite audiences through the platform adapter; returns empty results once closed. */
internal class PlatformAudiences(
    private val adapter: PlatformAudienceAdapter,
    private val onDeliveryFailure: (Throwable) -> Unit = {},
) : Audiences, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val failureThrottle = FailureThrottle()

    override fun console(): Console = if (closed.get()) CLOSED_CONSOLE else adapter.console()
    override fun player(uniqueId: UUID): OnlinePlayer? = if (closed.get()) null else adapter.player(uniqueId)
    override fun of(native: Any): Audience? = if (closed.get()) null else adapter.of(native)
    override fun players(): List<OnlinePlayer> = if (closed.get()) emptyList() else adapter.players().toList()
    override fun close() = closed.set(true)

    override fun audiences(): Iterable<Audience> =
        (listOf(console()) + players()).distinctByIdentity().map { FailureIsolatedAudience(it, failureThrottle, onDeliveryFailure) }

    private companion object {
        val CLOSED_CONSOLE = PlatformConsole(object : Audience {
            override fun pointers() = senderPointers("Console") { false }
        })

        fun Iterable<Audience>.distinctByIdentity(): List<Audience> {
            val seen = Collections.newSetFromMap(IdentityHashMap<Audience, Boolean>())
            return filter(seen::add)
        }
    }
}
