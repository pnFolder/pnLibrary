package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID

/** What one server may ask another to do through the link. Only the update controls of pnLibrary; no commands. */
enum class RemoteAction(
    /** The word used in the `/pn servers <name> <word>` command. */
    val word: String,
    /** Whether the action needs a plugin name. */
    val needsProduct: Boolean,
    /** The permission the person asking must have on the server they ask from. */
    val permission: String,
) {
    /** Look for updates now. */
    CHECK("check", false, "pnlibrary.updates.view"),

    /** Find and download the update of one plugin; it applies after a restart. */
    UPDATE("update", true, "pnlibrary.updates.download"),

    /** Turn automatic download on for one plugin. */
    AUTO_ON("auto-on", true, "pnlibrary.updates.settings"),

    /** Turn automatic download off for one plugin. */
    AUTO_OFF("auto-off", true, "pnlibrary.updates.settings"),
}

/**
 * A request for [action] sent to the server called [target].
 *
 * @property id unique id, which also stops a recorded request from being played again
 * @property target the server that should do it, or [LinkNames.PROXY]
 * @property origin the server that asked; the proxy fills it in from the connection the request came on
 * @property actor the name of the person who asked, for the update history
 * @property product the plugin, for actions that need one
 */
data class RemoteRequest(
    val id: UUID,
    val target: String,
    val origin: String,
    val actor: String,
    val action: RemoteAction,
    val product: String?,
    val sentAt: Instant,
)

/**
 * The answer to a [RemoteRequest].
 *
 * @property id id of the request
 * @property origin who asked, so the answer finds its way back
 * @property ok whether the action was carried out
 * @property text what happened, in words
 */
data class RemoteResult(val id: UUID, val origin: String, val ok: Boolean, val text: String, val sentAt: Instant)

/** Names used on the link. */
object LinkNames {
    /** The proxy as a target of a request. */
    const val PROXY = "proxy"

    /** The origin of a request made on the proxy itself. */
    const val PROXY_ORIGIN = "@proxy"
}
