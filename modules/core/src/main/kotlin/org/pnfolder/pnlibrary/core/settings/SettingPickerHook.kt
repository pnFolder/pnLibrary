package org.pnfolder.pnlibrary.core.settings

import org.pnfolder.pnlibrary.api.commands.CommandContext

/**
 * The link between `/pn settings pick` and the inventory menu: a platform that has menus sets [opener], the command calls it.
 * A platform without menus (a proxy) leaves it empty, and the command says so.
 */
class SettingPickerHook {
    /** Opens the list of values of the setting `key`, narrowed to `filter`, for the player who ran the command; `false` when it cannot. */
    @Volatile
    var opener: ((context: CommandContext, key: String, filter: String) -> Boolean)? = null
}
