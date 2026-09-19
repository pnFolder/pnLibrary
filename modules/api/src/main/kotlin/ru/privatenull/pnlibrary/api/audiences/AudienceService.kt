package ru.privatenull.pnlibrary.api.audiences

import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import java.util.UUID

/** A message receiver with identity and platform-backed permission checks. */
interface AudienceSender : LibraryAudience {
    val id: String
    val name: String
    val isConsole: Boolean
    val isPlayer: Boolean get() = !isConsole
    fun hasPermission(permission: String): Boolean
}

/** Resolves and combines audiences supplied by the active platform runtime. */
interface AudienceService {
    fun console(): AudienceSender
    fun player(uniqueId: UUID): LibraryPlayer?
    fun sender(native: Any): AudienceSender?
    fun onlinePlayers(): List<LibraryPlayer>
    fun all(): LibraryAudience
    fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience
}
