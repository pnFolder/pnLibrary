package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.config.LibraryConfigFiles
import java.nio.file.Path
import java.util.function.Supplier

/** Administrator policy from `downloads.yml`: whether downloads run, start automatically, and which destinations are allowed. */
internal data class DownloadConfiguration(
    val enabled: Boolean = true,
    val automatic: Boolean = false,
    val destinations: Set<DownloadDestination> = setOf(
        DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE,
    ),
) {
    companion object {
        /**
         * Reads the policy from [path], creating the file with defaults when it is missing.
         *
         * An unreadable file never blocks startup: conservative defaults are used and [warning] explains why.
         */
        fun load(path: Path, warning: (String) -> Unit = {}): DownloadConfiguration = try {
            LibraryConfigFiles.load(
                file = path,
                type = DownloadSettings::class.java,
                defaults = Supplier { DownloadSettings() },
                warning = warning,
            ).toConfiguration()
        } catch (error: Exception) {
            warning("downloads.yml не прочитан, используются значения по умолчанию: ${error.message}")
            DownloadConfiguration()
        }
    }
}
