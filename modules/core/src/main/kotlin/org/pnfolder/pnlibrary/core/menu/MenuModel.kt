package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.core.link.Link
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

enum class ClickType { LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, OTHER }

class MenuClick(val player: MenuViewer, val slot: Int, val click: ClickType)

fun interface MenuClickHandler {
    fun handle(click: MenuClick)
}

class MenuItem(val item: ItemSpec?, val handler: MenuClickHandler?)

class Menu(val title: String, val rows: Int, val items: Map<Int, MenuItem>)

class MenuBuilder internal constructor(private val title: String) {
    private var rows = DEFAULT_ROWS
    private val items = linkedMapOf<Int, MenuItem>()

    fun rows(value: Int): MenuBuilder = apply {
        require(value in 1..MAX_ROWS) { "Menu rows must be between 1 and 6" }
        rows = value
    }

    @JvmOverloads
    fun slot(index: Int, item: ItemSpec?, click: MenuClickHandler? = null): MenuBuilder = apply {
        require(index in 0 until rows * SLOTS_PER_ROW) { "Slot $index is outside inventory" }
        items[index] = MenuItem(item, click)
    }

    fun button(index: Int, item: ItemSpec, click: MenuClickHandler): MenuBuilder = slot(index, item, click)

    @JvmOverloads
    fun fill(item: ItemSpec, from: Int = 0, until: Int = rows * SLOTS_PER_ROW): MenuBuilder = apply {
        for (slot in from until until) if (slot !in items) items[slot] = MenuItem(item, null)
    }

    fun build(): Menu = Menu(title, rows, LinkedHashMap(items))

    private companion object {
        const val DEFAULT_ROWS = 6
        const val MAX_ROWS = 6
        const val SLOTS_PER_ROW = 9
    }
}

object Menus {
    fun chest(title: String): MenuBuilder = MenuBuilder(title)
}

/**
 * Whoever looks at the menu: a player on this server, or a player of another server whose pages are sent over the link.
 */
interface MenuViewer {
    val id: UUID

    val name: String

    val online: Boolean

    val remote: Boolean

    val actor: UpdateActor

    fun hasPermission(permission: String): Boolean

    fun open(menu: Menu)

    fun close()

    fun send(lines: List<Component>)

    /** Runs [command], without the slash, with the viewer's permissions; what it prints goes to the viewer. */
    fun runCommand(command: String)
}

data class OwnerInfo(val name: String, val version: String?, val authors: List<String>)

interface PluginDirectory {
    /** The plugin that [owner] is, or null when it is not one. */
    fun owner(owner: Any?): OwnerInfo?

    fun ownerNamed(name: String): OwnerInfo?

    /** The plugin whose code defines [type], or null. */
    fun ownerOf(type: Class<*>?): OwnerInfo?
}

interface MenuHost : PluginDirectory {
    fun sync(action: () -> Unit)

    fun later(delayMillis: Long, action: () -> Unit)

    /** Runs [action] away from that thread, where waiting for another server is allowed. */
    fun async(action: () -> Unit)

    /** Opens the menu of the server called [target] for [viewer]; false when that is not possible from here. */
    fun openRemote(viewer: MenuViewer, target: String): Boolean

    val link: Link get() = Link.OFF
}
