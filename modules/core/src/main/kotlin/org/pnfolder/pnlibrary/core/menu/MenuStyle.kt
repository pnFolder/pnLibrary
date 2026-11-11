package org.pnfolder.pnlibrary.core.menu

internal enum class Tone(val from: String, val to: String) {
    NORMAL("D19BFF", "F5C6FF"),
    GOOD("6EE7A0", "B8F5D2"),
    WARN("FDE047", "FFB347"),
    DANGER("FF7A7A", "FFB0A0"),
    MUTED("9AA4AD", "C9D0D6"),
}

internal class Row(val stack: ItemSpec, val click: MenuClickHandler? = null)

internal fun section(texture: String?, material: String, label: String, description: String, vararg parameters: Pair<String, String>): ItemSpec =
    icon(texture, material, label, Style.lore(listOf(description), parameters.toList(), Style.left("открыть")))

internal fun backItem(label: String = "Назад"): ItemSpec =
    icon(Icons.BACK, "ARROW", "« $label", Style.lore(emptyList(), emptyList(), Style.left("вернуться")), warn = true)

internal fun closeItem(): ItemSpec =
    icon(Icons.BACK, "BARRIER", "Закрыть", Style.lore(emptyList(), emptyList(), Style.left("закрыть меню")), danger = true)

internal fun title(text: String): String = MenuColors.legacy('0') + "pnLibrary " + MenuColors.legacy('5') + "» " + MenuColors.legacy('0') + text

internal fun frame(builder: MenuBuilder, rows: Int): MenuBuilder {
    val last = (rows - 1) * 9
    listOf(0, 1, 9, 7, 8, 17, last, last + 1, last - 9, last + 7, last + 8, last - 1).forEach { builder.slot(it, accent()) }
    return builder.fill(pane())
}

internal fun footerCorners(builder: MenuBuilder) {
    listOf(FOOTER_START, FOOTER_START + 1, FOOTER_END - 2, FOOTER_END - 1).forEach { builder.slot(it, accent()) }
}

internal fun pane(): ItemSpec = glass("BLACK_STAINED_GLASS_PANE", LEGACY_BLACK)

internal fun accent(): ItemSpec = glass("PURPLE_STAINED_GLASS_PANE", LEGACY_PURPLE)

internal fun glass(modern: String, legacyData: Short): ItemSpec = ItemSpec(null, modern, "STAINED_GLASS_PANE", " ", emptyList(), legacyData)

/**
 * A button: the textured head [texture] when the server can make one, otherwise the item [material]
 * (or [alternative] on old servers). The name colour says what the button is for.
 */
internal fun icon(
    texture: String?,
    material: String,
    label: String,
    lore: List<String>,
    alternative: String? = null,
    good: Boolean = false,
    warn: Boolean = false,
    danger: Boolean = false,
    muted: Boolean = false,
): ItemSpec {
    val tone = when {
        danger -> Tone.DANGER
        warn -> Tone.WARN
        good -> Tone.GOOD
        muted -> Tone.MUTED
        else -> Tone.NORMAL
    }
    return ItemSpec(texture, material, alternative, Style.name(label, tone), lore)
}

internal object Style {
    private val DIM = MenuColors.color("&#64546E")
    private val PLAIN = MenuColors.color("&#F7F2F7")
    private val DESCRIPTION = MenuColors.color(" &#E9DDF2")
    private val SUB = MenuColors.color("   &#8B7A99↳ &#F7F2F7")
    private val TAGS = listOf("D19BFF", "5BC8F5", "6EE7A0", "FFB347", "FF9BD2", "FDE047", "B79CFF")

    fun name(label: String, tone: Tone): String = MenuColors.gradient(label, tone.from, tone.to)

    private fun click(label: String, from: String, to: String, action: String): String =
        "$DIM ▸ " + MenuColors.gradient(label, from, to) + "  $PLAIN" + action

    fun left(action: String): String = click("Клик", "D19BFF", "F5C6FF", action)

    fun right(action: String): String = click("Правый клик", "FF9BD2", "FF7A9B", action)

    fun shiftLeft(action: String): String = click("Shift + клик", "5BC8F5", "B5ECFF", action)

    fun description(text: String): String = DESCRIPTION + text

    fun choices(options: List<String>, selected: Int): List<String> = options.mapIndexed { index, label ->
        if (index == selected) {
            MenuColors.color("   &#6EE7A0➤ ") + MenuColors.gradient(label, "D19BFF", "FF9BD2")
        } else {
            MenuColors.color("   &#64546E· &#8B7A99") + label
        }
    }

    fun danger(text: String): String = MenuColors.color(" &#FF7A7A") + text

    /** A bracketed label such as `[ Версия ]`, in a colour that stays the same for the same word. */
    fun tag(label: String): String = "$DIM [ " + MenuColors.color("&#${TAGS[Math.floorMod(label.hashCode(), TAGS.size)]}") + label + " $DIM] $PLAIN"

    /**
     * Description lines, a blank line, `[ label ] value` lines (an empty label continues the line above with an
     * indented `↳ value`), a blank line, then the click hints; framed by blank lines.
     */
    fun lore(
        lines: List<String>,
        parameters: List<Pair<String, String>>,
        vararg hints: String,
        extra: List<String> = emptyList(),
    ): List<String> = buildList {
        add("")
        lines.forEach { add(description(it)) }
        if (lines.isNotEmpty() && parameters.isNotEmpty()) add("")
        parameters.forEach { (label, value) -> add(if (label.isEmpty()) SUB + value else tag(label) + value) }
        if (extra.isNotEmpty()) {
            add("")
            addAll(extra)
        }
        if (hints.isNotEmpty()) {
            add("")
            addAll(hints)
        }
        add("")
    }
}

internal object Icons {
    const val OVERVIEW = "35747010d84aa56483b75f6243d94f34c534603584b2cc68a45bf365840105fd"
    const val PLUGINS = "c16808126eec0cd41d6040e211985719272c388d32ecae795d47f7a9cee71113"
    const val UPDATES = "c2be2091a46355cd30e537e428174d316401e5fbbda53ac3a7e8d7426ec6a814"
    const val PROBLEMS = "375b36c3945fdaa0c740818531a8f45bacd2696ce2ab85a9ea45290accf180cc"
    const val HISTORY = "505f7080eb7ad15a68ae161322cd2ba25da5d24cda452d0e88c8e496ebc6b7a0"
    const val BACKUPS = "5318ee5278e08d9e6fe9163ec4267c791f5248a357f5f780d6346812c704eb8f"
    const val CHECK = "45556083c9cde19f54bf78a421cb9731f60f1d3de3cf584f54b2d43677df2a7"
    const val SUPPORT = "16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f"
    const val BACK = "69ea1d86247f4af351ed1866bca6a3040a06c68177c78e42316a1098e60fb7d3"
    const val NEXT = "8271a47104495e357c3e8e80f511a9f102b0700ca9b88e88b795d33ff20105eb"
    const val TOGGLE = "5f9e02768935593321437f2d6709a59a6e5437eb4301631965e9df5359ac87ef"
    const val LOCKED = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065"
    const val SETTINGS = "5949a18cb52c293fe7de7ba1014671340ed7ff8e5d705b2d60bf84d53148e04"
    const val TOOLS = "79924bcfd7ec6ea437438b75b72ee41a2b57104dba25ecafa6118e68e1b81c9d"
    const val RELEASES = "c67d5d37cd64ce3fb57377d25d6152aa4ac2e37954248d5d91f88fbd791f4766"
    const val CHANGELOG = "97a530f5c5742bf19e175a4d78ad435ac0f4396d3b5464bd6182ab382aca417d"
    const val RESTART = "87d885b32b0dd2d6b7f1b582a34186f8a5373c46589a273423132b448b803462"
    const val ROLLBACK = "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065"
    const val CLOCK = "9f84735fc9c760e95eaf10cec4f10edb5f3822a5ff9551eeb5095135d1ffa302"
    const val RESET = "aa8182f994d6426052f86c50b24c016b2ba94a25aff54abf122034193a1358e3"
    const val PLUGIN = "79924bcfd7ec6ea437438b75b72ee41a2b57104dba25ecafa6118e68e1b81c9d"
}
