package org.pnfolder.pnlibrary.core.link

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.time.Instant
import java.util.UUID

/** What a viewer asks of the server whose menu it looks at. */
enum class ViewAction {
    /** Open the home page. */
    OPEN,

    /** Click a slot of the page on screen. */
    CLICK,

    /** The viewer closed the menu. */
    CLOSE,
}

/**
 * A viewer's step in the menu of another server.
 *
 * @property session identifies the open menu on both sides
 * @property target the server whose menu is shown
 * @property origin the server of the viewer; the proxy fills it in
 * @property viewer the viewer's name, for the update history
 * @property permissions the permissions the viewer holds, as the viewer's server sees them
 * @property slot the clicked slot, for [ViewAction.CLICK]
 * @property click the kind of click, a `ClickType` name, for [ViewAction.CLICK]
 * @property sentAt when the viewer made it
 */
data class ViewRequest(
    val session: UUID,
    val target: String,
    val origin: String,
    val action: ViewAction,
    val viewer: String,
    val permissions: Set<String>,
    val slot: Int,
    val click: String,
    val sentAt: Instant,
)

/**
 * One page of a menu, drawn on the server that owns it and shown by the viewer's server.
 *
 * @property session identifies the open menu
 * @property to the server of the viewer
 * @property title the title of the inventory
 * @property rows the number of rows
 * @property items the stacks by slot, each as the owning server serialized it
 * @property notices chat messages for the viewer, as JSON components, see [ViewNotices]
 * @property closed whether the viewer should close the menu
 * @property sentAt when it was made
 */
class ViewFrame(
    val session: UUID,
    val to: String,
    val title: String,
    val rows: Int,
    val items: Map<Int, ByteArray>,
    val notices: List<String>,
    val closed: Boolean,
    val sentAt: Instant,
)

/** Turns chat messages into the text that travels in a [ViewFrame] and back. */
object ViewNotices {
    private val gson = GsonComponentSerializer.gson()

    /** [message] as JSON. */
    fun encode(message: Component): String = gson.serialize(message)

    /** The message in [json], or null when it cannot be read. */
    fun decode(json: String): Component? = runCatching { gson.deserialize(json) }.getOrNull()
}
