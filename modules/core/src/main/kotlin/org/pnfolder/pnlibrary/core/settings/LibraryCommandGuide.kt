package org.pnfolder.pnlibrary.core.settings

/** The permission that opens every command of the library. */
internal const val GUIDE_ADMIN = "pnlibrary.admin"

/** Where a command exists. */
internal enum class GuideScope(val note: String?) {
    EVERYWHERE(null),
    GAME_SERVER("Есть только на игровом сервере."),
    PROXY("Есть только на прокси (BungeeCord, Velocity)."),
}

/**
 * One line of the command guide.
 *
 * @param usage the command as it is typed, or the name of a right that has no command
 * @param permission the permission node, or `null` when the entry needs none
 * @param description what it does, one short sentence
 * @param orAdmin whether `pnlibrary.admin` opens the command too
 * @param scope where the command exists
 * @param note one more thing the administrator should know, or `null`
 */
internal class GuideEntry(
    val usage: String,
    val permission: String?,
    val description: String,
    val orAdmin: Boolean = false,
    val scope: GuideScope = GuideScope.EVERYWHERE,
    val note: String? = null,
) {
    /** Whether [permission] is a pattern such as `pnlibrary.currency.<валюта>.list`, not one node of plugin.yml. */
    val isPattern: Boolean get() = permission?.contains('<') == true
}

internal class GuideSection(val title: String, val entries: List<GuideEntry>)

/**
 * The one list of every `/pn` command and every permission of the library: the text of `Команды.txt` is made from it and
 * a test keeps it equal to the permissions of `plugin.yml`.
 *
 * Whoever adds a command or a permission adds it here; nothing else lists them.
 */
internal object LibraryCommandGuide {
    private const val ADMIN = GUIDE_ADMIN

    val sections: List<GuideSection> = listOf(
        updatesGuide,
        journalGuide,
        pluginsGuide,
        settingsGuide,
        networkGuide,
        menuGuide,
    )

    /** How to read the list: the rules that hold for all entries. */
    val notes: List<String> = listOf(
        "Консоль выполняет все команды без прав.",
        "«Право: A или pnlibrary.admin» значит, что подойдёт любое из двух. Если в заметке сказано «нужно и право …», нужны оба.",
        "Подкоманды /pn updates … требуют ещё pnlibrary.updates.view или pnlibrary.admin, " +
            "подкоманды /pn update … требуют ещё pnlibrary.updates.download или pnlibrary.admin.",
        "На игровом сервере команда называется /pn (есть и /pnlib, /pnlibrary). " +
            "На прокси она называется /pnproxy (есть и /pnp), в этом файле она везде записана как /pn.",
    )

    /** Every entry of every section. */
    val entries: List<GuideEntry> = sections.flatMap(GuideSection::entries)

    /** Every permission node the guide names, patterns excluded, plus `pnlibrary.admin`. */
    val permissions: Set<String> = entries.filterNot(GuideEntry::isPattern).mapNotNull(GuideEntry::permission).toSet() + ADMIN

    /** The permissions that exist only on a proxy and so are not declared in the Bukkit plugin.yml. */
    val proxyOnly: Set<String> = entries.partition { it.scope == GuideScope.PROXY }
        .let { (proxy, others) -> proxy.mapNotNull(GuideEntry::permission).toSet() - others.mapNotNull(GuideEntry::permission).toSet() - ADMIN }

    /** The lines of the guide between the heading and the signature of `Команды.txt`. */
    fun lines(): List<String> = buildList {
        for (section in sections) {
            add("")
            add(section.title)
            add("-".repeat(section.title.length))
            for (entry in section.entries) {
                add("")
                add(entry.usage)
                add("    Право: ${rights(entry)}")
                add("    ${entry.description}")
                entry.scope.note?.let { add("    $it") }
                entry.note?.let { add("    $it") }
            }
        }
    }

    private fun rights(entry: GuideEntry): String {
        val permission = entry.permission ?: return "не нужно"
        return when {
            permission == ADMIN -> permission
            entry.orAdmin -> "$permission или $ADMIN"
            else -> permission
        }
    }
}
