package org.pnfolder.pnlibrary.core.updates.catalog

/**
 * Commands a catalog page links to, so one view serves both the Bukkit `/pn updates` tree and the
 * console-only `/pnupdate` command.
 *
 * @property root base of the catalog commands, for example `/pn updates`
 * @property update builds the command that prepares an update for one product; null hides update buttons
 * @property check command that refreshes the release catalogs
 * @property interactive whether the reader can click; without it buttons are replaced by a list of commands
 * @property ascii whether to avoid characters outside ASCII, for consoles with a legacy code page
 * @property editable whether the reader may change settings; otherwise the settings card is read-only
 */
data class UpdateCatalogRoutes(
    val root: String,
    val update: ((String) -> String)? = null,
    val check: String = "$root check",
    val interactive: Boolean = true,
    val ascii: Boolean = false,
    val editable: Boolean = true,
) {
    /** Command that lists every installed product. */
    val list: String get() = "$root list"

    /** Command that lists products with a newer release available. */
    val available: String get() = "$root available"

    /** Command that shows the update history. */
    val history: String get() = "$root history"

    /** Command that lists the available rollback backups. */
    val backups: String get() = "$root backups"

    /** Command that shows the details of one product. */
    fun info(product: String): String = "$root info $product"

    /** Command that opens the settings of one product. */
    fun settings(product: String): String = "$root settings $product"

    /** Command that shows the update history of one product. */
    fun history(product: String): String = "$root history plugin $product"

    /** Command that changes one setting of one product to [value]. */
    fun set(product: String, setting: String, value: String): String = "$root set $product $setting $value"

    /** Command that restores the backup [id], optionally with its configuration files. */
    fun rollback(id: Any, configurations: Boolean = false): String = "$root rollback $id" + if (configurations) " configs" else ""
}
