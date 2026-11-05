package org.pnfolder.pnlibrary.core.menu

/**
 * The inventory menu of `/pn gui`: everything the update commands do, on several connected screens.
 *
 * Screens: the home page, the list of plugins (all, with updates, with problems), the card of one plugin, its
 * channel, pause and releases, the history of one plugin or of all and one entry of it, the saved backups with a
 * rollback choice, the journal with one event, the tasks of every plugin, and the plugin events (event bus) with their listeners, days and
 * recorded calls.
 * Every action calls the same services as the commands and asks for the same permissions, so the audit record is the
 * same, and every answer is a chat notice in the library's own style. Buttons are textured heads with an ordinary
 * item as the fallback, so the menu opens on every server from 1.8.
 */
class LibraryMenu(
    data: MenuData,
    host: MenuHost,
    remoteName: String? = null,
) {
    private val router = MenuRouter(MenuSession(data, host, remoteName))

    fun home(player: MenuViewer) = router.home(player)

    fun open(player: MenuViewer) = router.open(player)
}
