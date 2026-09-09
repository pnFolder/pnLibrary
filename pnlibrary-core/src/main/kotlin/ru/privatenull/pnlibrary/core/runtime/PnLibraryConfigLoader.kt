package ru.privatenull.pnlibrary.core.runtime

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Loads the installed runtime configuration from `plugins/pnLibrary/config.yml`. */
object PnLibraryConfigLoader {
    @JvmStatic
    fun load(dataFolder: Path): PnLibraryConfig {
        Files.createDirectories(dataFolder)
        val file = dataFolder.resolve("config.yml")
        if (!Files.exists(file)) writeDefault(file)
        require(!Files.isSymbolicLink(file)) { "pnLibrary config.yml must not be a symbolic link" }
        require(Files.size(file) <= MAX_CONFIG_BYTES) { "pnLibrary config.yml exceeds 256 KiB" }

        val options = LoaderOptions().apply {
            maxAliasesForCollections = 20
            isAllowDuplicateKeys = false
        }
        val loaded = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
            Yaml(SafeConstructor(options)).load<Any?>(reader)
        }
        val values = loaded as? Map<*, *> ?: error("pnLibrary config.yml must contain a YAML object")
        values.keys.forEach { rawKey ->
            val key = rawKey as? String ?: error("pnLibrary config keys must be strings")
            require(key in KNOWN_KEYS) { "Unknown pnLibrary config option: $key" }
        }
        val defaults = PnLibraryConfig()
        return PnLibraryConfig(
            upload = values.boolean("upload", defaults.upload),
            uploadMode = when (val mode = values.string("upload-mode", defaults.uploadMode)) {
                "encrypted-catbox" -> "encrypted"
                else -> mode
            },
            uploadProviders = values.strings("upload-providers").ifEmpty { defaults.uploadProviders },
            uploadEndpoint = values.string("upload-endpoint", defaults.uploadEndpoint),
            uploadPublicBase = values.string("upload-public-base", defaults.uploadPublicBase),
            uploadPublicKey = values.string("upload-public-key", defaults.uploadPublicKey),
            uploadKeyId = values.string("upload-key-id", defaults.uploadKeyId),
            allowPlaintext = values.boolean("allow-plaintext", defaults.allowPlaintext),
            configs = values.boolean("configs", defaults.configs),
            logs = values.boolean("logs", defaults.logs),
            logRecords = values.int("log-records", defaults.logRecords),
            cooldownSeconds = values.int("cooldown-seconds", defaults.cooldownSeconds),
            keepReports = values.int("keep-reports", defaults.keepReports),
            maxReportBytes = values.int("max-report-bytes", defaults.maxReportBytes),
            deleteAfterDays = values.int("delete-after-days", defaults.deleteAfterDays),
            excludedPaths = values.strings("excluded-paths"),
            secretKeyPatterns = values.strings("secret-key-patterns"),
            redactValuePatterns = values.strings("redact-value-patterns"),
        )
    }

    private fun writeDefault(file: Path) {
        val temporary = Files.createTempFile(file.parent, "config.yml.", ".tmp")
        try {
            Files.write(temporary, DEFAULT_CONFIG.toByteArray(StandardCharsets.UTF_8))
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun Map<*, *>.string(key: String, default: String): String =
        get(key)?.toString() ?: default

    private fun Map<*, *>.boolean(key: String, default: Boolean): Boolean = when (val value = get(key)) {
        null -> default
        is Boolean -> value
        else -> value.toString().toBooleanStrictOrNull()
            ?: error("pnLibrary config '$key' must be true or false")
    }

    private fun Map<*, *>.int(key: String, default: Int): Int = when (val value = get(key)) {
        null -> default
        is Number -> value.toInt()
        else -> value.toString().toIntOrNull()
            ?: error("pnLibrary config '$key' must be an integer")
    }

    private fun Map<*, *>.strings(key: String): List<String> = when (val value = get(key)) {
        null -> emptyList()
        is List<*> -> value.map { it?.toString() ?: error("pnLibrary config '$key' contains null") }
        else -> error("pnLibrary config '$key' must be a YAML list")
    }

    private const val MAX_CONFIG_BYTES = 256L * 1024L
    private val KNOWN_KEYS = setOf(
        "upload", "upload-mode", "upload-providers", "upload-endpoint", "upload-public-base", "upload-public-key",
        "upload-key-id", "allow-plaintext", "configs", "logs", "log-records", "cooldown-seconds",
        "keep-reports", "max-report-bytes", "delete-after-days", "excluded-paths",
        "secret-key-patterns", "redact-value-patterns",
    )
    private val DEFAULT_CONFIG = """
        # Diagnostics are encrypted before upload. Set upload to false for local-only reports.
        upload: true
        upload-mode: encrypted
        upload-providers: [catbox, fileio]
        allow-plaintext: false
        upload-endpoint: https://api.mclo.gs/1/log
        upload-public-base: https://mclo.gs/
        upload-public-key: ''
        upload-key-id: support-1

        configs: true
        logs: true
        log-records: 200
        cooldown-seconds: 10
        keep-reports: 10
        max-report-bytes: 8388608
        delete-after-days: 90

        excluded-paths: []
        secret-key-patterns: []
        redact-value-patterns: []
    """.trimIndent() + "\n"
}
