package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.time.Duration

fun interface MenuClickHandler { fun handle(click: MenuClick) }
fun interface MenuCloseHandler { fun handle(close: MenuClose) }
fun interface MenuOpenHandler { fun handle(session: MenuSession) }
fun interface MenuRenderer { fun render(session: MenuSession) }

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
    val player: Player get() = session.player
    fun close() = session.close()
    fun refresh() = session.refresh()
}

data class MenuClose(val session: MenuSession)

data class MenuItem(
    val item: ItemStack?,
    val handler: MenuClickHandler?,
    val editable: Boolean,
)

data class Menu(
    val title: String,
    val rows: Int,
    val type: MenuType,
    val items: Map<Int, MenuItem>,
    val renderer: MenuRenderer?,
    val openHandler: MenuOpenHandler?,
    val closeHandler: MenuCloseHandler?,
    val clickHandler: MenuClickHandler?,
    val cancelPlayerInventory: Boolean,
) {
    val size: Int get() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}

enum class MenuType(val defaultSize: Int) {
    CHEST(54), ANVIL(3), HOPPER(5), DISPENSER(9), DROPPER(9), WORKBENCH(10),
}

interface MenuSession : AutoCloseable {
    val player: Player
    val menu: Menu
    val inventory: Inventory
    fun set(slot: Int, item: ItemStack?)
    fun get(slot: Int): ItemStack?
    fun refresh()
    fun refreshAfter(delay: Duration)
    override fun close()
}

class MenuBuilder internal constructor(private val title: String, private val type: MenuType) {
    private var rows = 6
    private val items = linkedMapOf<Int, MenuItem>()
    private var renderer: MenuRenderer? = null
    private var openHandler: MenuOpenHandler? = null
    private var closeHandler: MenuCloseHandler? = null
    private var clickHandler: MenuClickHandler? = null
    private var cancelPlayerInventory = true

    fun rows(value: Int) = apply { require(value in 1..6); rows = value }
    fun cancelPlayerInventory(value: Boolean) = apply { cancelPlayerInventory = value }
    fun slot(index: Int, item: ItemStack?, editable: Boolean = false, click: MenuClickHandler? = null) = apply {
        require(index in 0 until effectiveSize()) { "Slot $index is outside inventory" }
        items[index] = MenuItem(item?.clone(), click, editable)
    }
    fun button(index: Int, item: ItemStack, click: MenuClickHandler) = slot(index, item, false, click)
    fun editable(index: Int, item: ItemStack) = slot(index, item, true, null)
    fun editable(index: Int) = slot(index, null, true, null)
    fun onClick(handler: MenuClickHandler) = apply { clickHandler = handler }
    fun fill(item: ItemStack, from: Int = 0, until: Int = effectiveSize()) = apply {
        require(from >= 0 && until <= effectiveSize() && from <= until)
        for (slot in from until until) if (slot !in items) items[slot] = MenuItem(item.clone(), null, false)
    }
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
    fun render(renderer: MenuRenderer) = apply { this.renderer = renderer }
    fun onOpen(handler: MenuOpenHandler) = apply { openHandler = handler }
    fun onClose(handler: MenuCloseHandler) = apply { closeHandler = handler }
    fun build() = Menu(
        title, rows, type, items.toMap(), renderer, openHandler,
        closeHandler, clickHandler, cancelPlayerInventory,
    )
    private fun effectiveSize() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}

object Menus {
    @JvmStatic fun chest(title: String) = MenuBuilder(title, MenuType.CHEST)
    @JvmStatic fun anvil(title: String) = MenuBuilder(title, MenuType.ANVIL)
    @JvmStatic fun hopper(title: String) = MenuBuilder(title, MenuType.HOPPER)
    @JvmStatic fun dispenser(title: String) = MenuBuilder(title, MenuType.DISPENSER)
    @JvmStatic fun dropper(title: String) = MenuBuilder(title, MenuType.DROPPER)
    @JvmStatic fun workbench(title: String) = MenuBuilder(title, MenuType.WORKBENCH)
}
