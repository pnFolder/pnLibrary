package org.pnfolder.pnlibrary.bukkit.inventory

/** Java-friendly entry points for each supported menu shape. */
object Menus {

    /** Creates a six-row chest builder; call [MenuBuilder.rows] to resize it. */
    @JvmStatic
    fun chest(title: String): MenuBuilder = MenuBuilder(title, MenuType.CHEST)

    @JvmStatic
    fun anvil(title: String): MenuBuilder = MenuBuilder(title, MenuType.ANVIL)

    @JvmStatic
    fun hopper(title: String): MenuBuilder = MenuBuilder(title, MenuType.HOPPER)

    @JvmStatic
    fun dispenser(title: String): MenuBuilder = MenuBuilder(title, MenuType.DISPENSER)

    @JvmStatic
    fun dropper(title: String): MenuBuilder = MenuBuilder(title, MenuType.DROPPER)

    @JvmStatic
    fun workbench(title: String): MenuBuilder = MenuBuilder(title, MenuType.WORKBENCH)
}
