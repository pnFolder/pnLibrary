package ru.privatenull.pnlibrary.api.audiences

import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import java.util.UUID

/** A message receiver with identity and platform-backed permission checks. */
interface AudienceSender : LibraryAudience {
    /** Stable platform-independent identifier for this sender. */
    val id: String
    /** Current human-readable sender name. */
    val name: String
    /** Whether this sender represents the server console. */
    val isConsole: Boolean
    /** Whether this sender represents a connected player. */
    val isPlayer: Boolean get() = !isConsole
    /** Returns whether this sender is granted [permission] by the active platform. */
    fun hasPermission(permission: String): Boolean
}

/** Resolves and combines audiences supplied by the active platform runtime. */
interface AudienceService {
    /** Returns the server-console audience. */
    fun console(): AudienceSender

    /** Returns the online player identified by [uniqueId], or `null` when absent. */
    fun player(uniqueId: UUID): LibraryPlayer?

    /** Adapts a platform-native sender object, or returns `null` when unsupported. */
    fun sender(native: Any): AudienceSender?

    /** Returns a snapshot of all players currently online. */
    fun onlinePlayers(): List<LibraryPlayer>

    /** Returns a dynamic audience containing every receiver exposed by the platform. */
    fun all(): LibraryAudience

    /** Combines [audiences] into one receiver that forwards every operation. */
    fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience
}
