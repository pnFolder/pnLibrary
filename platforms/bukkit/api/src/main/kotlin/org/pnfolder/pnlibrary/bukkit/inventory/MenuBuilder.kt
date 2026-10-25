package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.inventory.ItemStack
import java.util.Collections

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
    fun rows(value: Int): MenuBuilder = apply {
        require(value in 1..6) { "Menu rows must be between 1 and 6" }
        rows = value
    }

    /** Controls whether clicks in the player's lower inventory are cancelled. */
    fun cancelPlayerInventory(value: Boolean): MenuBuilder = apply { cancelPlayerInventory = value }

    /** Defines one validated slot, cloning [item] to isolate the menu definition. */
    @JvmOverloads
    fun slot(index: Int, item: ItemStack?, editable: Boolean = false, click: MenuClickHandler? = null): MenuBuilder = apply {
        require(index in 0 until effectiveSize()) { "Slot $index is outside inventory" }
        items[index] = MenuItem(item?.clone(), click, editable)
    }

    /** Defines a non-editable item with a mandatory click handler. */
    fun button(index: Int, item: ItemStack, click: MenuClickHandler): MenuBuilder = slot(index, item, false, click)

    /** Defines an editable slot initially containing [item]. */
    fun editable(index: Int, item: ItemStack): MenuBuilder = slot(index, item, true, null)

    /** Defines an initially empty editable slot. */
    fun editable(index: Int): MenuBuilder = slot(index, null, true, null)

    /** Sets the handler invoked after any slot-specific handler. */
    fun onClick(handler: MenuClickHandler): MenuBuilder = apply { clickHandler = handler }

    /** Fills undefined slots in `[from, until)` with clones of [item]. */
    @JvmOverloads
    fun fill(item: ItemStack, from: Int = 0, until: Int = effectiveSize()): MenuBuilder = apply {
        require(from >= 0 && until <= effectiveSize() && from <= until) {
            "Menu fill range [$from, $until) is outside inventory"
        }
        for (slot in from until until) if (slot !in items) items[slot] = MenuItem(item.clone(), null, false)
    }

    /** Fills undefined outer chest slots; fixed-size non-chest menus are rejected. */
    fun border(item: ItemStack): MenuBuilder = apply {
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
    fun render(renderer: MenuRenderer): MenuBuilder = apply { this.renderer = renderer }

    /** Sets the callback invoked after the inventory opens and renders. */
    fun onOpen(handler: MenuOpenHandler): MenuBuilder = apply { openHandler = handler }

    /** Sets the callback invoked for normal session closure. */
    fun onClose(handler: MenuCloseHandler): MenuBuilder = apply { closeHandler = handler }

    /** Creates an immutable menu and snapshot of all current slot definitions. */
    fun build(): Menu = Menu(
        title = title,
        rows = rows,
        type = type,
        items = Collections.unmodifiableMap(LinkedHashMap(items)),
        renderer = renderer,
        openHandler = openHandler,
        closeHandler = closeHandler,
        clickHandler = clickHandler,
        cancelPlayerInventory = cancelPlayerInventory,
    )
    /** Returns the slot count implied by the current menu shape and rows. */
    private fun effectiveSize() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}
