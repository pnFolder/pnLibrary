package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.config.LibraryConfigFiles
import java.nio.file.Path
import java.util.function.Supplier

/** Loads the installed runtime configuration from `plugins/pnLibrary/config.yml`. */
internal object PnLibraryConfigLoader {
    @JvmStatic
    fun load(dataFolder: Path, warning: (String) -> Unit = {}): PnLibraryConfig =
        LibraryConfigFiles.load(
            file = dataFolder.resolve("config.yml"),
            type = DiagnosticsSettings::class.java,
            defaults = Supplier { DiagnosticsSettings() },
            warning = warning,
        ).toConfig()
}
