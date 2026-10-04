package ru.privatenull.pnlibrary.core.updates

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Reads, migrates and validates the persisted update policy. */
internal class UpdateConfigurationLoader(
    private val warning: (String) -> Unit,
) {
    private var malformed = false

    fun load(file: Path): UpdateConfiguration {
        malformed = false
        Files.createDirectories(file.toAbsolutePath().parent)
        if (!Files.exists(file)) {
            val defaults = UpdateConfiguration()
            writeAtomic(file, defaults.toYaml())
            return defaults
        }

        val source = Files.readString(file, StandardCharsets.UTF_8)
        val parsed = parseYaml(source)
        migrateLegacyConfiguration(file, parsed)?.let { return it }
        return readCurrentConfiguration(file, source, parsed)
    }

    private fun migrateLegacyConfiguration(
        file: Path,
        parsed: Map<String, Any?>,
    ): UpdateConfiguration? {
        if (parsed.containsKey("updates")) return null
        if (!parsed.containsKey("channel") && !parsed.containsKey("auto-download")) return null

        val channel = (parsed["channel"] as? String)
            ?.lowercase()
            ?.takeIf(ALLOWED_CHANNELS::contains)
            ?: "stable"
        val migrated = UpdateConfiguration(
            downloads = UpdateConfiguration.Downloads(automatic = parsed["auto-download"] == true),
            legacyChannel = channel,
        )
        val backup = file.resolveSibling("${file.fileName}.pre-orchestrator.bak")
        if (!Files.exists(backup)) Files.copy(file, backup)
        writeAtomic(file, migrated.toYaml())
        return migrated
    }

    private fun readCurrentConfiguration(
        file: Path,
        source: String,
        parsed: Map<String, Any?>,
    ): UpdateConfiguration {
        val root = parsed.child("updates") ?: emptyMap<String, Any?>().also { markMalformed() }
        ensureLibrarySection(file, source, parsed, root.child("library"))
        val result = UpdatePolicyReader(::markMalformed).read(root)
        if (malformed) warning("Invalid update configuration values were replaced with conservative defaults")
        return result
    }

    private fun ensureLibrarySection(
        file: Path,
        source: String,
        parsed: Map<String, Any?>,
        library: Map<String, Any?>?,
    ) {
        if (library != null || !parsed.containsKey("updates") || source.contains("  library:\n")) return
        val block = """

  library:
    # Канал релизов для самой pnLibrary: stable, rc, beta, alpha или dev.
    channel: stable
    # Разрешить автоматическую загрузку новой версии самой pnLibrary.
    automatic-download: false
        """.trimEnd()
        runCatching { writeAtomic(file, source.trimEnd() + block + "\n") }
    }

    private fun markMalformed() {
        malformed = true
    }

    companion object {
        private val ALLOWED_CHANNELS = setOf("stable", "rc", "beta", "alpha", "dev")

        @Suppress("UNCHECKED_CAST")
        private fun parseYaml(source: String): Map<String, Any?> = try {
            val strictBooleans = source.replace(
                Regex("(?i)(:\\s*)(yes|no|on|off)(?=\\s*[,}#\\r\\n])"),
                "$1\"$2\"",
            )
            (Yaml(SafeConstructor(LoaderOptions())).load<Any?>(strictBooleans) as? Map<*, *>)
                ?.entries
                ?.associate { it.key.toString() to it.value }
                ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        private fun Map<String, Any?>.child(key: String): Map<String, Any?>? = get(key).asStringMap()

        private fun Any?.asStringMap(): Map<String, Any?>? =
            (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

        private fun writeAtomic(file: Path, text: String) {
            val temporary = Files.createTempFile(file.toAbsolutePath().parent, file.fileName.toString(), ".tmp")
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8)
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}
