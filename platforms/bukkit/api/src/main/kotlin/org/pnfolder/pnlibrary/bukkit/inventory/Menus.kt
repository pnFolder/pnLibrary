package org.pnfolder.pnlibrary.bukkit.inventory

/** Java-friendly entry points for each supported menu shape. */
object Menus {

    /** Creates a six-row chest builder; call [MenuBuilder.rows] to resize it. */
    @JvmStatic
    fun chest(title: String): MenuBuilder = MenuBuilder(title, MenuType.CHEST)

    /** Creates an anvil builder. */
    @JvmStatic
    fun anvil(title: String): MenuBuilder = MenuBuilder(title, MenuType.ANVIL)

    /** Creates a hopper builder. */
    @JvmStatic
    fun hopper(title: String): MenuBuilder = MenuBuilder(title, MenuType.HOPPER)

    /** Creates a dispenser builder. */
    @JvmStatic
    fun dispenser(title: String): MenuBuilder = MenuBuilder(title, MenuType.DISPENSER)

    /** Creates a dropper builder. */
    @JvmStatic
    fun dropper(title: String): MenuBuilder = MenuBuilder(title, MenuType.DROPPER)

    /** Creates a workbench builder. */
    @JvmStatic
    fun workbench(title: String): MenuBuilder = MenuBuilder(title, MenuType.WORKBENCH)
}
