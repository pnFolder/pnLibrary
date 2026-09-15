package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.time.Duration

/** Handles one click inside a menu's top inventory. */
fun interface MenuClickHandler {
    /** Processes immutable click details and may update or close the session. */
    fun handle(click: MenuClick)
}

/** Receives a normal player-driven menu close notification. */
fun interface MenuCloseHandler {
    /** Processes [close] after the session has been removed from the service. */
    fun handle(close: MenuClose)
}

/** Receives a session after its inventory has opened and initial rendering has completed. */
fun interface MenuOpenHandler {
    /** Processes the newly opened [session]. */
    fun handle(session: MenuSession)
}

/** Rebuilds dynamic inventory contents for a menu session. */
fun interface MenuRenderer {
    /** Renders current application state into [session] through [MenuSession.set]. */
    fun render(session: MenuSession)
}

/**
 * Immutable details of one top-inventory click.
 *
 * A slot-specific [MenuItem.handler] runs before [Menu.clickHandler]. Non-editable slots
 * are cancelled before either handler runs. Item stacks reflect the Bukkit event and
 * should be cloned by handlers that retain or mutate them asynchronously.
 *
 * @property session active session receiving the event
 * @property slot raw zero-based slot in the top inventory
 * @property item item occupying the clicked slot, if any
 * @property cursor item currently held by the cursor, if any
 * @property click physical Bukkit click type
 * @property action inventory action inferred by Bukkit
 * @property topInventory always true for dispatched menu clicks
 * @property renameText current nonblank anvil input when available
 */
data class MenuClick(
    val session: MenuSession,
    val slot: Int,
    val item: ItemStack?,
    val cursor: ItemStack?,
    val click: ClickType,
    val action: InventoryAction,
    val topInventory: Boolean,
    val renameText: String? = null,
) {
    /** Player associated with [session]. */
    val player: Player get() = session.player
    /** Closes the active session. */
    fun close() = session.close()
    /** Immediately invokes the configured renderer when the session is still active. */
    fun refresh() = session.refresh()
}

/** Normal close callback containing the session that just finished. */
data class MenuClose(
    /** Finished session whose inventory remains available for final inspection. */
    val session: MenuSession,
)

/**
 * Immutable initial definition and click behavior for one menu slot.
 *
 * @property item cloned initial item, or `null` for an empty slot
 * @property handler slot-specific click handler, or `null`
 * @property editable whether Bukkit may apply player inventory actions to this slot
 */
data class MenuItem(
    val item: ItemStack?,
    val handler: MenuClickHandler?,
    val editable: Boolean,
)

/**
 * Immutable menu definition reusable across players and sessions.
 *
 * Static [items] are copied into each new inventory before [renderer] runs. The renderer
 * can then replace them with player-specific or current application state.
 */
data class Menu(
    /** Native Bukkit inventory title. */
    val title: String,
    /** Chest row count; ignored by non-chest menu types. */
    val rows: Int,
    /** Native inventory shape. */
    val type: MenuType,
    /** Static slot definitions keyed by raw top-inventory slot. */
    val items: Map<Int, MenuItem>,
    /** Optional dynamic renderer invoked on open and explicit refresh. */
    val renderer: MenuRenderer?,
    /** Optional callback invoked after initial rendering and opening. */
    val openHandler: MenuOpenHandler?,
    /** Optional callback for normal closes while the owner remains enabled. */
    val closeHandler: MenuCloseHandler?,
    /** Optional handler invoked after a clicked slot's own handler. */
    val clickHandler: MenuClickHandler?,
    /** Whether clicks in the player's lower inventory are cancelled. */
    val cancelPlayerInventory: Boolean,
) {
    /** Effective top-inventory size in slots. */
    val size: Int get() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}

/**
 * Supported Bukkit inventory shapes and their fixed native slot counts.
 *
 * @property defaultSize native top-inventory slot count; chest menus replace
 * this value with the configured row count multiplied by nine
 */
enum class MenuType(val defaultSize: Int) {
    /** Variable chest inventory from one through six rows. */ CHEST(54),
    /** Three-slot anvil inventory with optional rename text. */ ANVIL(3),
    /** Five-slot hopper inventory. */ HOPPER(5),
    /** Nine-slot dispenser inventory. */ DISPENSER(9),
    /** Nine-slot dropper inventory. */ DROPPER(9),
    /** Ten-slot crafting workbench inventory. */ WORKBENCH(10),
}

/** One player's live view of a reusable [Menu] definition. */
interface MenuSession : AutoCloseable {
    /** Player viewing this session. */
    val player: Player
    /** Immutable definition from which the inventory was created. */
    val menu: Menu
    /** Native top inventory owned by this session. */
    val inventory: Inventory
    /** Replaces [slot] with [item]; `null` clears it. */
    fun set(slot: Int, item: ItemStack?)
    /** Returns the current item in [slot], if any. */
    fun get(slot: Int): ItemStack?
    /** Invokes [Menu.renderer] immediately unless the session has finished. */
    fun refresh()
    /** Schedules [refresh] through the player-aware task scheduler after [delay]. */
    fun refreshAfter(delay: Duration)
    /** Closes this session. Safe to repeat. */
    override fun close()
}

/** Fluent builder for immutable [Menu] definitions. */
class MenuBuilder internal constructor(private val title: String, private val type: MenuType) {
    private var rows = 6
    private val items = linkedMapOf<Int, MenuItem>()
    private var renderer: MenuRenderer? = null
    private var openHandler: MenuOpenHandler? = null
    private var closeHandler: MenuCloseHandler? = null
    private var clickHandler: MenuClickHandler? = null
    private var cancelPlayerInventory = true

    /** Sets chest rows from one through six; ignored by fixed-size menu types. */
    fun rows(value: Int) = apply {
        require(value in 1..6) { "Menu rows must be between 1 and 6" }
        rows = value
    }
    /** Controls whether clicks in the player's lower inventory are cancelled. */
    fun cancelPlayerInventory(value: Boolean) = apply { cancelPlayerInventory = value }
    /** Defines one validated slot, cloning [item] to isolate the menu definition. */
    fun slot(index: Int, item: ItemStack?, editable: Boolean = false, click: MenuClickHandler? = null) = apply {
        require(index in 0 until effectiveSize()) { "Slot $index is outside inventory" }
        items[index] = MenuItem(item?.clone(), click, editable)
    }
    /** Defines a non-editable item with a mandatory click handler. */
    fun button(index: Int, item: ItemStack, click: MenuClickHandler) = slot(index, item, false, click)
    /** Defines an editable slot initially containing [item]. */
    fun editable(index: Int, item: ItemStack) = slot(index, item, true, null)
    /** Defines an initially empty editable slot. */
    fun editable(index: Int) = slot(index, null, true, null)
    /** Sets the handler invoked after any slot-specific handler. */
    fun onClick(handler: MenuClickHandler) = apply { clickHandler = handler }
    /** Fills undefined slots in `[from, until)` with clones of [item]. */
    fun fill(item: ItemStack, from: Int = 0, until: Int = effectiveSize()) = apply {
        require(from >= 0 && until <= effectiveSize() && from <= until) {
            "Menu fill range [$from, $until) is outside inventory"
        }
        for (slot in from until until) if (slot !in items) items[slot] = MenuItem(item.clone(), null, false)
    }
    /** Fills undefined outer chest slots; fixed-size non-chest menus are rejected. */
    fun border(item: ItemStack) = apply {
        require(type == MenuType.CHEST) { "Borders are available for chest menus" }
        for (slot in 0 until effectiveSize()) {
            val row = slot / 9
            val column = slot % 9
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                if (slot !in items) items[slot] = MenuItem(item.clone(), null, false)
            }
        }
    }
    /** Sets the dynamic renderer invoked on open and refresh. */
    fun render(renderer: MenuRenderer) = apply { this.renderer = renderer }
    /** Sets the callback invoked after the inventory opens and renders. */
    fun onOpen(handler: MenuOpenHandler) = apply { openHandler = handler }
    /** Sets the callback invoked for normal session closure. */
    fun onClose(handler: MenuCloseHandler) = apply { closeHandler = handler }
    /** Creates an immutable menu and snapshot of all current slot definitions. */
    fun build() = Menu(
        title = title,
        rows = rows,
        type = type,
        items = items.toMap(),
        renderer = renderer,
        openHandler = openHandler,
        closeHandler = closeHandler,
        clickHandler = clickHandler,
        cancelPlayerInventory = cancelPlayerInventory,
    )
    private fun effectiveSize() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}

/** Java-friendly entry points for each supported menu shape. */
object Menus {
    /** Creates a six-row chest builder; call [MenuBuilder.rows] to resize it. */
    @JvmStatic fun chest(title: String) = MenuBuilder(title, MenuType.CHEST)
    /** Creates an anvil builder. */
    @JvmStatic fun anvil(title: String) = MenuBuilder(title, MenuType.ANVIL)
    /** Creates a hopper builder. */
    @JvmStatic fun hopper(title: String) = MenuBuilder(title, MenuType.HOPPER)
    /** Creates a dispenser builder. */
    @JvmStatic fun dispenser(title: String) = MenuBuilder(title, MenuType.DISPENSER)
    /** Creates a dropper builder. */
    @JvmStatic fun dropper(title: String) = MenuBuilder(title, MenuType.DROPPER)
    /** Creates a workbench builder. */
    @JvmStatic fun workbench(title: String) = MenuBuilder(title, MenuType.WORKBENCH)
}
