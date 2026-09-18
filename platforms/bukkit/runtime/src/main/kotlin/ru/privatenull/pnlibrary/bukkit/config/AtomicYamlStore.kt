package ru.privatenull.pnlibrary.bukkit.config

import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

/**
 * Persists Bukkit YAML using validate-before-replace semantics.
 *
 * The configuration is written to a sibling temporary file, optionally transformed, and parsed
 * back through [YamlConfiguration] before it can replace the target. An atomic move is preferred;
 * filesystems without atomic-move support fall back to a replacing move. The temporary file is
 * removed on every exit path.
 */
internal object AtomicYamlStore {

    fun interface FilePostProcessor {
        /**
         * Mutates or validates the temporary file before read-back validation.
         *
         * Throwing [IOException] aborts the save and preserves the existing target.
         */
        @Throws(IOException::class)
        fun apply(file: Path)
    }

    /**
     * Safely persists [yaml] to [target].
     *
     * @param logger receives a concise warning for expected I/O or YAML validation failures
     * @param postProcessor optional operation applied only to the temporary file
     * @return `true` after replacement succeeds, or `false` for expected I/O/validation failures
     */
    @JvmStatic
    @JvmOverloads
    fun save(
        yaml: FileConfiguration,
        target: File,
        logger: Logger,
        postProcessor: FilePostProcessor = FilePostProcessor { },
    ): Boolean {
        var temporary: Path? = null
        try {
            val parent = target.absoluteFile.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                throw IOException("cannot create directory $parent")
            }
            val parentPath = parent?.toPath() ?: target.toPath().toAbsolutePath().parent
            temporary = Files.createTempFile(parentPath, "${target.name}.", ".tmp")
            yaml.save(temporary.toFile())
            postProcessor.apply(temporary)

            val readback = YamlConfiguration()
            readback.load(temporary.toFile())
            try {
                Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return true
        } catch (e: Exception) {
            when (e) {
                is IOException, is InvalidConfigurationException -> {
                    logger.warning("Не удалось безопасно сохранить ${target.name}: ${e.message}")
                    return false
                }
                else -> throw e
            }
        } finally {
            if (temporary != null) {
                runCatching { Files.deleteIfExists(temporary) }
            }
        }
    }
}
