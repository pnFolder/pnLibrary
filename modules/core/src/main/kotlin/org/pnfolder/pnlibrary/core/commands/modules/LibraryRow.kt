package org.pnfolder.pnlibrary.core.commands.modules

import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/**
 * One library registered by a plugin or a module, as the menu and `/pn modules libs` show it; plain values, so it also
 * crosses to another server.
 *
 * @property owner `plugin` when the whole plugin registered it, `plugin:module` for one module
 * @property conflict another owner uses a different version of the same library
 * @property file where the jar lies on this server, or `null` while it is not known
 * @property place the same place counted from the server folder (`plugins/pnLibrary/libraries/...`), for people to read
 * @property size bytes of that jar, or -1 when unknown
 * @property trust [GIVEN] when the author gave a sum the jar is checked against, [NONE] when the jar is not checked
 * @property state `LOADED`, `DISABLED` (an administrator switched it off), `FAILED` or `SKIPPED`
 * @property autoLoad whether it is loaded with the server; saved across restarts
 * @property error why it could not be loaded
 * @property restartRequired switched off while in use: it goes away at the next restart
 * @property scope `PLUGIN`, `ISOLATED` or `LIBRARY`, see [org.pnfolder.pnlibrary.api.libraries.LibraryScope]
 */
data class LibraryRow(
    val owner: String,
    val title: String,
    val coordinates: String,
    val version: String,
    val description: String?,
    val repositories: List<String>,
    val isolated: Boolean,
    val required: Boolean,
    val verified: Boolean,
    val conflict: Boolean,
    val file: String? = null,
    val size: Long = -1,
    val trust: String = NONE,
    val scope: String = "PLUGIN",
    val place: String? = null,
    val state: String = "LOADED",
    val autoLoad: Boolean = true,
    val error: String? = null,
    val restartRequired: Boolean = false,
) {
    /** The plugin that owns the library, also when a module registered it. */
    val plugin: String get() = owner.substringBefore(':')

    /** The module that registered the library, or `null` when the whole plugin did. */
    val module: String? get() = owner.substringAfter(':', "").ifEmpty { null }

    /** `плагину shop` or `модулю rewards плагина shop`: whose the library is, in words. */
    val ownerText: String get() = module?.let { "модулю $it плагина $plugin" } ?: "плагину $plugin"

    /** The folder of the jar counted from the server folder, without the file name. */
    val folder: String? get() = place?.substringBeforeLast('/', "")?.ifEmpty { null }

    /** The file name of the jar. */
    val fileName: String? get() = place?.substringAfterLast('/')

    /** Whether this row belongs to [name]: the plugin itself, or any of its modules, or exactly that module. */
    fun belongsTo(name: String): Boolean = owner.equals(name, true) || owner.startsWith("$name:", true)

    companion object {
        const val GIVEN = "GIVEN"
        const val NONE = "NONE"

        private fun locateLoaded(coordinates: String): String? =
            org.pnfolder.pnlibrary.spi.libraries.ExternalLibraries.fileOf(LibraryRow::class.java.classLoader, coordinates)

        /** [file] counted from the server folder with `/`, or as it is when it lies elsewhere. */
        fun placeOf(file: String, root: java.nio.file.Path? = DiskUsage.pluginsFolder?.parent): String {
            val path = java.nio.file.Paths.get(file).toAbsolutePath().normalize()
            val inside = root?.toAbsolutePath()?.normalize()?.takeIf { path.startsWith(it) }
            return (inside?.relativize(path) ?: path).toString().replace('\\', '/')
        }

        /** Every library of every plugin and module of this server. */
        fun all(library: PnLibrary, locate: (String) -> String? = ::locateLoaded): List<LibraryRow> {
            val usage = library.plugins.libraryUsage()
            val versions = usage.groupBy { it.library.group to it.library.artifact }.mapValues { entry -> entry.value.map { it.library.version }.toSet() }
            return usage.map { use ->
                val item = use.library
                val file = locate(item.coordinates)
                val trust = if (item.sources.isNotEmpty() && item.sources.all { it.sha256 != null }) GIVEN else NONE
                LibraryRow(
                    owner = use.owner,
                    title = item.title,
                    coordinates = item.coordinates,
                    version = item.version,
                    description = item.description,
                    repositories = item.allSources.map { it.url },
                    isolated = item.isolated,
                    required = item.required,
                    verified = trust != NONE,
                    conflict = (versions[item.group to item.artifact]?.size ?: 1) > 1,
                    file = file,
                    trust = trust,
                    scope = item.scope.name,
                    place = file?.let { placeOf(it) },
                    state = use.status?.state?.name ?: "LOADED",
                    autoLoad = use.status?.autoLoad ?: true,
                    error = use.status?.error,
                    restartRequired = use.status?.restartRequired ?: false,
                    size = file?.let { java.io.File(it) }?.takeIf { it.isFile }?.length() ?: -1,
                )
            }.sortedWith(compareBy({ it.owner }, { it.title }))
        }
    }
}
