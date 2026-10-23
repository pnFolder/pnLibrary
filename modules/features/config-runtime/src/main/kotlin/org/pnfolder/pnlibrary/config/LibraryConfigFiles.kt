package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigOptions
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Loads the library's own YAML files (`config.yml`, `updates.yml`, `downloads.yml`) through the same
 * code-first configuration system that plugins use: defaults, comments and validation come from the model.
 *
 * Keys that no longer exist in the model are removed after a backup, so a file never keeps stale options.
 */
object LibraryConfigFiles {
    private const val MAX_BYTES = 256L * 1024L

    private val OPTIONS = ConfigOptions(
        unknownValues = UnknownValuePolicy.REMOVE,
        naming = ConfigNamingStrategy.KEBAB_CASE,
    )

    /**
     * Creates the file with defaults when it is missing, synchronizes it with [type], and returns the value.
     *
     * @param warning receiver for recoverable problems such as removed keys
     * @throws IllegalArgumentException when the file is a symbolic link, too large or invalid
     */
    fun <T : Any> load(
        file: Path,
        type: Class<T>,
        defaults: Supplier<T>,
        validator: ConfigValueValidator<T> = ConfigValueValidator { emptyList() },
        warning: (String) -> Unit = {},
    ): T {
        require(!Files.isSymbolicLink(file)) { "${file.fileName} must not be a symbolic link" }
        require(!Files.exists(file) || Files.size(file) <= MAX_BYTES) { "${file.fileName} exceeds 256 KiB" }

        val result = StandaloneConfigs.yaml(file, type, defaults, OPTIONS, validator, forwarding(warning)).load()
        if (result.removedPaths.isNotEmpty()) {
            val backup = result.backup?.name?.let { "; копия прежнего файла: $it" }.orEmpty()
            warning("${file.fileName}: удалены неизвестные параметры (${result.removedPaths.joinToString()})$backup")
        }
        return result.value
    }

    private fun forwarding(warning: (String) -> Unit): Logger =
        Logger.getAnonymousLogger().apply {
            useParentHandlers = false
            level = Level.WARNING
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) {
                    if (record.level.intValue() >= Level.WARNING.intValue()) warning(record.message)
                }

                override fun flush() = Unit

                override fun close() = Unit
            })
        }
}
