package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity

/**
 * Initiator recorded in update history; identity is audit metadata, not an authorization token.
 *
 * @property kind origin of the operation
 * @property id stable platform identity, such as a player UUID or console ID
 * @property name display name captured when the operation was requested
 */
data class UpdateActor(val kind: Kind, val id: String, val name: String) {
    /** Origin of an update operation. */
    enum class Kind {
        /** Internal scheduled work. */
        SYSTEM,
        /** Server or proxy console. */
        CONSOLE,
        /** Authenticated platform player. */
        PLAYER,
        /** Another integration invoking the service. */
        INTEGRATION,
        /** A caller which supplied no identity. */
        UNKNOWN,
    }

    init {
        require(id.isNotBlank() && id.length <= 256 && id.none(Char::isISOControl)) { "Invalid update actor ID" }
        require(name.isNotBlank() && name.length <= 256 && name.none(Char::isISOControl)) { "Invalid update actor name" }
    }

    /** Standard identities for automatic operations and callers without explicit attribution. */
    companion object {
        /** Internal scheduled work, not an administrator command. */
        @JvmField val SYSTEM = UpdateActor(Kind.SYSTEM, "system", PnLibraryIdentity.NAME)
        /** Compatibility calls which did not supply an initiator. */
        @JvmField val UNKNOWN = UpdateActor(Kind.UNKNOWN, "unknown", "Unknown")
    }
}
