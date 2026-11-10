package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.IdentityHashMap
import java.util.UUID

/** Open menus by player and by owning plugin. Regions open menus in parallel on Folia, so access is locked. */
internal class MenuSessions {
    private val lock = Any()
    private val byPlayer = HashMap<UUID, BukkitMenuSession>()
    private val byOwner = IdentityHashMap<Plugin, MutableSet<BukkitMenuSession>>()

    @Volatile
    var isClosed = false
        private set

    /** The menu open for [player], if any. */
    fun of(player: Player): BukkitMenuSession? = synchronized(lock) { byPlayer[player.uniqueId] }

    /** Menus opened by [owner]. */
    fun of(owner: Plugin): List<BukkitMenuSession> = synchronized(lock) { byOwner[owner]?.toList().orEmpty() }

    /** Tracks [session]; fails once the service is closed or the owner is disabled. */
    fun add(session: BukkitMenuSession) = synchronized(lock) {
        check(!isClosed && session.owner.isEnabled) { "Menu service or owner is disabled" }
        byPlayer[session.player.uniqueId] = session
        byOwner.getOrPut(session.owner) { linkedSetOf() }.add(session)
    }

    /** Stops tracking [session]. */
    fun remove(session: BukkitMenuSession) = synchronized(lock) {
        byPlayer.remove(session.player.uniqueId, session)
        val owned = byOwner[session.owner] ?: return@synchronized
        owned.remove(session)
        if (owned.isEmpty()) byOwner.remove(session.owner)
    }

    /** Marks the registry closed and returns the menus still open, or `null` if it was already closed. */
    fun shutdown(): List<BukkitMenuSession>? = synchronized(lock) {
        if (isClosed) return null
        isClosed = true
        byPlayer.values.toList()
    }
}

/** Runs every action even when some fail, then throws the first failure with the rest suppressed. */
internal fun runEach(actions: List<() -> Unit>) {
    val failures = actions.mapNotNull { action -> runCatchingNonFatal(action).exceptionOrNull() }
    val first = failures.firstOrNull() ?: return
    failures.drop(1).forEach(first::addSuppressed)
    throw first
}
