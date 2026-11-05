package org.pnfolder.pnlibrary.core.placeholders

/**
 * Full address of a placeholder: `plugin/module:name`.
 *
 * The module is always present; a plugin registered without modules uses [DEFAULT_MODULE].
 */
internal data class PlaceholderAddress(val plugin: String, val module: String, val name: String) {
    init {
        require(PART.matches(plugin) && '_' !in plugin) { "Invalid plugin in placeholder address: $plugin" }
        require(PART.matches(module)) { "Invalid module in placeholder address: $module" }
        require(NAME.matches(name)) { "Invalid name in placeholder address: $name" }
    }

    override fun toString() = "$plugin/$module:$name"

    /** The `%prefix_module~name%` body PlaceholderAPI receives, without the percent signs. */
    fun forPlaceholderApi(prefix: String) = "${prefix}_$module~$name"

    companion object {
        const val DEFAULT_MODULE = "main"
        private val PART = Regex("[a-z0-9][a-z0-9_-]*")
        private val NAME = Regex("[a-zA-Z0-9_.{}-]+")

        /** Parses `plugin/module:name`; returns `null` for anything else, including shortened forms. */
        fun parse(text: String): PlaceholderAddress? {
            val slash = text.indexOf('/')
            val colon = text.indexOf(':')
            if (slash <= 0 || colon <= slash + 1 || colon == text.length - 1) return null
            val plugin = text.substring(0, slash)
            val module = text.substring(slash + 1, colon)
            val name = text.substring(colon + 1)
            return of(plugin, module, name)
        }

        /** Builds an address, or `null` when a part cannot be written in it (for example a plugin id with `_`). */
        fun of(plugin: String, module: String, name: String): PlaceholderAddress? =
            runCatching { PlaceholderAddress(plugin, module, name) }.getOrNull()
    }
}
