package ru.privatenull.pnlibrary.core.updates.catalog

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
    val list: String get() = "$root list"
    val available: String get() = "$root available"
    val history: String get() = "$root history"
    val backups: String get() = "$root backups"
    fun info(product: String): String = "$root info $product"
    fun settings(product: String): String = "$root settings $product"
    fun history(product: String): String = "$root history plugin $product"
    fun set(product: String, setting: String, value: String): String = "$root set $product $setting $value"
    fun rollback(id: Any, configurations: Boolean = false): String = "$root rollback $id" + if (configurations) " configs" else ""
}
