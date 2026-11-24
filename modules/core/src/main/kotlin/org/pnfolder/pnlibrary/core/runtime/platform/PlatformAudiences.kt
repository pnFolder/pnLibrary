package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.pnfolder.pnlibrary.api.audience.Audiences
import org.pnfolder.pnlibrary.api.audience.LibraryPlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter
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

    override fun console(): Audience = if (closed.get()) CLOSED_CONSOLE else adapter.console()
    override fun player(uniqueId: UUID): LibraryPlayer? = if (closed.get()) null else adapter.player(uniqueId)
    override fun of(native: Any): Audience? = if (closed.get()) null else adapter.of(native)
    override fun players(): List<LibraryPlayer> = if (closed.get()) emptyList() else adapter.players().toList()
    override fun filter(predicate: (LibraryPlayer) -> Boolean): Audience = group { players().filter(predicate) }
    override fun server(): Audience = group { listOf(console()) + players() }
    override fun close() = closed.set(true)

    private fun group(receivers: () -> Iterable<Audience>): Audience = object : ForwardingAudience {
        override fun audiences(): Iterable<Audience> =
            receivers().distinctByIdentity().map { FailureIsolatedAudience(it, failureThrottle, onDeliveryFailure) }
    }

    private companion object {
        val CLOSED_CONSOLE = object : Audience {
            override fun pointers() = senderPointers("Console") { false }
        }

        fun Iterable<Audience>.distinctByIdentity(): List<Audience> {
            val seen = Collections.newSetFromMap(IdentityHashMap<Audience, Boolean>())
            return filter(seen::add)
        }
    }
}
