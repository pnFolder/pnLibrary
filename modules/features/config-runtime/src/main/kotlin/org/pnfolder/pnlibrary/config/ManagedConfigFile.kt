package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.config.yaml.DatabasePath
import java.nio.file.Path

/**
 * A file registered through a configuration scope.
 *
 * @property pluginId plugin that registered the file
 * @property directory data folder of the plugin
 * @property path path relative to [directory], with `/` separators
 * @property secretPaths dotted paths of the `@ConfigSecret` fields
 * @property databases sections marked `@BackupDatabase`
 */
class ManagedConfigFile(
    val pluginId: PluginId,
    val directory: Path,
    val path: String,
    val secretPaths: List<String>,
    val databases: List<DatabasePath> = emptyList(),
)
