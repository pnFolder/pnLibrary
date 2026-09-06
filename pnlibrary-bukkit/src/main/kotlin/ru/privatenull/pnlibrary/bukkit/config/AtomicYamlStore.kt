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
 * Safely saves a Bukkit [FileConfiguration] using a temp file and atomic move.
 */
object AtomicYamlStore {

    fun interface FilePostProcessor {
        @Throws(IOException::class)
        fun apply(file: Path)
    }

    @JvmStatic
    @JvmOverloads
    fun save(
        yaml: FileConfiguration,
        target: File,
        logger: Logger,
        postProcessor: FilePostProcessor = FilePostProcessor { }
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
