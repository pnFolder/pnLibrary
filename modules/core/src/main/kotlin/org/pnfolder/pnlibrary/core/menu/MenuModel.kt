package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import java.util.UUID

/**
 * One item of a menu page, described without any platform type.
 *
 * Each platform turns it into its own stack: the head with [texture] when it can make one, otherwise [material]
 * (or [alternative] on old servers), named [name] and described by [lore]. [legacyData] is the damage value of coloured
 * glass on servers before 1.13.
 */
data class ItemSpec(
    val texture: String?,
    val material: String,
    val alternative: String?,
    val name: String,
    val lore: List<String>,
    val legacyData: Short = 0,
)

/** The kind of click on a slot. */
enum class ClickType { LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, OTHER }

/** A click on a slot: who clicked, which slot and how. */
class MenuClick(val player: MenuViewer, val slot: Int, val click: ClickType)

/** What happens when a slot is clicked. */
fun interface MenuClickHandler {
    /** Handles [click]. */
    fun handle(click: MenuClick)
}

/** A slot of a page: its item and what a click does. */
class MenuItem(val item: ItemSpec?, val handler: MenuClickHandler?)

/** One page of the menu: a chest of [rows] rows with [items] by slot. */
class Menu(val title: String, val rows: Int, val items: Map<Int, MenuItem>)

/** Builds a [Menu]. */
class MenuBuilder internal constructor(private val title: String) {
    private var rows = DEFAULT_ROWS
    private val items = linkedMapOf<Int, MenuItem>()

    /** Sets the number of rows, 1 to 6. */
    fun rows(value: Int): MenuBuilder = apply {
        require(value in 1..MAX_ROWS) { "Menu rows must be between 1 and 6" }
        rows = value
    }

    /** Puts [item] in [index]; a click runs [click] when given. */
    @JvmOverloads
    fun slot(index: Int, item: ItemSpec?, click: MenuClickHandler? = null): MenuBuilder = apply {
        require(index in 0 until rows * SLOTS_PER_ROW) { "Slot $index is outside inventory" }
        items[index] = MenuItem(item, click)
    }

    /** Puts [item] in [index] and runs [click] when it is clicked. */
    fun button(index: Int, item: ItemSpec, click: MenuClickHandler): MenuBuilder = slot(index, item, click)

    /** Puts [item] in every free slot from [from] up to [until]. */
    @JvmOverloads
    fun fill(item: ItemSpec, from: Int = 0, until: Int = rows * SLOTS_PER_ROW): MenuBuilder = apply {
        for (slot in from until until) if (slot !in items) items[slot] = MenuItem(item, null)
    }

    /** The page. */
    fun build(): Menu = Menu(title, rows, LinkedHashMap(items))

    private companion object {
        const val DEFAULT_ROWS = 6
        const val MAX_ROWS = 6
        const val SLOTS_PER_ROW = 9
    }
}

/** Entry point for building pages. */
object Menus {
    /** Starts a chest page called [title]. */
    fun chest(title: String): MenuBuilder = MenuBuilder(title)
}

/**
 * Whoever looks at the menu: a player on this server, or a player of another server whose pages are sent over the link.
 */
interface MenuViewer {
    /** Stable identity. */
    val id: UUID

    /** Name for messages and the update history. */
    val name: String

    /** Whether the viewer can still be reached. */
    val online: Boolean

    /** Whether the viewer is on another server. */
    val remote: Boolean

    /** Who the update history records for what this viewer does. */
    val actor: UpdateActor

    /** Whether the viewer holds [permission]. */
    fun hasPermission(permission: String): Boolean

    /** Shows [menu] to the viewer. */
    fun open(menu: Menu)

    /** Closes the viewer's menu. */
    fun close()

    /** Sends [lines] to the viewer as chat messages. */
    fun send(lines: List<Component>)

    /** Runs [command], without the slash, with the viewer's permissions; what it prints goes to the viewer. */
    fun runCommand(command: String)
}

/** A plugin as the menu lists it. */
data class OwnerInfo(val name: String, val version: String?, val authors: List<String>)

/** What the menu needs from the platform it runs on. */
interface MenuHost {
    /** Runs [action] on the thread that may touch the platform; the answers of asynchronous work come back through it. */
    fun sync(action: () -> Unit)

    /** Runs [action] on that thread after [delayMillis]. */
    fun later(delayMillis: Long, action: () -> Unit)

    /** The plugin that [owner] is, or null when it is not one. */
    fun owner(owner: Any?): OwnerInfo?

    /** The plugin called [name], or null. */
    fun ownerNamed(name: String): OwnerInfo?

    /** The plugin whose code defines [type], or null. */
    fun ownerOf(type: Class<*>?): OwnerInfo?

    /** Opens the menu of the server called [target] for [viewer]; false when that is not possible from here. */
    fun openRemote(viewer: MenuViewer, target: String): Boolean
}
