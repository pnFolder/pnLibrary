package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

/**
 * Auditable principal that initiated a currency transaction.
 *
 * @property type category used for filtering and reporting
 * @property id stable nonblank identifier within that category
 */
data class CurrencyActor(val type: CurrencyActorType, val id: String) {
    init { require(id.isNotBlank()) { "Currency actor ID must not be blank" } }
    /** Factories for the supported audited principal categories. */
    companion object {
        /** Creates a player actor from its stable UUID. */
        @JvmStatic
        fun player(id: UUID): CurrencyActor = CurrencyActor(CurrencyActorType.PLAYER, id.toString())
        /** Creates an actor for a registered plugin. */
        @JvmStatic
        fun plugin(id: PluginId): CurrencyActor = CurrencyActor(CurrencyActorType.PLUGIN, id.value)
        /** Creates an actor for a named application service. */
        @JvmStatic
        fun service(id: String): CurrencyActor = CurrencyActor(CurrencyActorType.SERVICE, id)
        /** Creates the canonical current-server actor. */
        @JvmStatic
        fun server(): CurrencyActor = CurrencyActor(CurrencyActorType.SERVER, "server")
        /** Creates an internal system actor with a caller-defined identifier. */
        @JvmStatic
        fun system(id: String): CurrencyActor = CurrencyActor(CurrencyActorType.SYSTEM, id)
    }
}
