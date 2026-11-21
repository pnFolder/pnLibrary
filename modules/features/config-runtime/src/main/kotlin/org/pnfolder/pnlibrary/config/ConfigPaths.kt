package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigRoot
import org.pnfolder.pnlibrary.api.config.MissingFilePolicy
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the configuration files of one plugin may live, and what a path may be.
 *
 * Every path a plugin gives is checked here: it must stay inside its root folder, must not leave it through a link, and
 * must name a YAML file. Kept apart from the service so the rules that keep a plugin out of other folders can be read
 * and tested on their own.
 *
 * @property directory the data folder of the plugin
 * @property plugin the plugin the files belong to, for messages
 */
internal class ConfigPaths(private val directory: Path, private val plugin: PluginId) {
    /** The folder paths of [root] are counted from. */
    fun rootDirectory(root: ConfigRoot): Path = when (root) {
        ConfigRoot.PLUGIN -> directory
        ConfigRoot.PLUGINS -> directory.parent
        ConfigRoot.SERVER -> directory.parent?.parent
        ConfigRoot.EXTERNAL -> error("An external path is absolute and has no root folder")
    } ?: error("The ${root.name.lowercase()} folder is not known for $plugin")

    /** Someone else's files get strict handling whatever the options ask: never created, always backed up, unknown keys kept. */
    fun effective(options: ConfigOptions): ConfigOptions =
        if (options.root == ConfigRoot.PLUGIN || options.root == ConfigRoot.EXTERNAL) options else options.copy(
            missingFile = MissingFilePolicy.FAIL,
            backups = true,
            unknownValues = if (options.unknownValues == UnknownValuePolicy.REMOVE) UnknownValuePolicy.PRESERVE else options.unknownValues,
        )

    /** An absolute path chosen by the plugin itself; only the file extension is checked. */
    fun external(path: String, relative: Path): Path {
        require(relative.isAbsolute) { "An external configuration path must be absolute: $path" }
        requireYaml(path)
        return relative
    }

    /** The absolute file for [path] under the folder of [root]; rejects paths outside it, links out of it and other file extensions. */
    fun checked(path: String, relative: Path, root: ConfigRoot): Path {
        val base = rootDirectory(root)
        val label = if (root == ConfigRoot.PLUGIN) "plugin directory" else "${root.name.lowercase()} folder"
        require(!relative.isAbsolute && !relative.startsWith("..")) {
            "Configuration path must stay inside the $label"
        }
        requireYaml(path)
        val target = base.resolve(relative).normalize()
        require(target.startsWith(base)) { "Configuration path escapes the $label" }
        if (Files.exists(target) && Files.exists(base)) {
            require(target.toRealPath().startsWith(base.toRealPath())) { "Configuration path leaves the $label through a link" }
        }
        return target
    }

    private fun requireYaml(path: String) {
        require(path.endsWith(".yml", true) || path.endsWith(".yaml", true)) {
            "Configuration file must use .yml or .yaml"
        }
    }
}
