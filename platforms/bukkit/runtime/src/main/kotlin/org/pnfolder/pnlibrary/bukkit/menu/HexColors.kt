package org.pnfolder.pnlibrary.bukkit.menu

/** Hex colours in item text, which servers before 1.16 cannot show. */
internal object HexColors {
    /** `ChatColor.of` arrived together with hex colours in 1.16. */
    val supported: Boolean = runCatching { Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String::class.java) }.isSuccess

    private val hexCode = Regex("§x(§[0-9A-Fa-f]){6}")

    /** On a server older than 1.16 replaces each hex colour in [text] with the nearest basic colour. */
    fun downgrade(text: String): String =
        if (supported) text else hexCode.replace(text) { match -> nearest(match.value.filter { it != '§' }.drop(1).toInt(HEX)) }

    private fun nearest(rgb: Int): String = BASIC.minBy { (color, _) ->
        val red = (color shr 16 and 0xFF) - (rgb shr 16 and 0xFF)
        val green = (color shr 8 and 0xFF) - (rgb shr 8 and 0xFF)
        val blue = (color and 0xFF) - (rgb and 0xFF)
        red * red + green * green + blue * blue
    }.second

    private const val HEX = 16

    private val BASIC = listOf(
        0x000000 to "§0", 0x0000AA to "§1", 0x00AA00 to "§2", 0x00AAAA to "§3", 0xAA0000 to "§4", 0xAA00AA to "§5",
        0xFFAA00 to "§6", 0xAAAAAA to "§7", 0x555555 to "§8", 0x5555FF to "§9", 0x55FF55 to "§a", 0x55FFFF to "§b",
        0xFF5555 to "§c", 0xFF55FF to "§d", 0xFFFF55 to "§e", 0xFFFFFF to "§f",
    )
}
