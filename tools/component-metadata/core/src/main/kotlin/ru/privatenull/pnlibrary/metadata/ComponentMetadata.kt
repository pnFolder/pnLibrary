package ru.privatenull.pnlibrary.metadata

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val COMPONENT_ID = Regex("[a-z0-9][a-z0-9_.-]*")

/**
 * Metadata that pnLibrary can inspect without loading classes from an untrusted JAR.
 *
 * @property id normalized component identifier
 * @property version component semantic version
 * @property apiMinimum oldest supported pnLibrary API generation
 * @property apiMaximum newest supported pnLibrary API generation
 */
data class ComponentMetadata(
    val id: String,
    val version: String,
    val apiMinimum: Int,
    val apiMaximum: Int,
) {
    init {
        require(COMPONENT_ID.matches(id)) { "component id must match ${COMPONENT_ID.pattern}: $id" }
        require(version.isNotBlank()) { "component version must not be blank" }
        require(apiMinimum > 0) { "minimum pnLibrary API must be positive" }
        require(apiMaximum >= apiMinimum) { "maximum pnLibrary API must be >= minimum" }
    }
}

/** Canonical writer shared by the Gradle and Maven integrations. */
object ComponentMetadataWriter {
    /** Canonical path of the descriptor relative to the root of a JAR. */
    const val ENTRY = "META-INF/pnlibrary/component.json"

    /** Encodes [metadata] as deterministic UTF-8 JSON accepted by pnLibrary runtime. */
    @JvmStatic
    fun encode(metadata: ComponentMetadata): ByteArray = buildString {
        append("{\n")
        append("  \"schema\": 1,\n")
        append("  \"component\": \"").append(escape(metadata.id)).append("\",\n")
        append("  \"version\": \"").append(escape(metadata.version)).append("\",\n")
        append("  \"pnLibraryApi\": {\n")
        append("    \"minimum\": ").append(metadata.apiMinimum).append(",\n")
        append("    \"maximum\": ").append(metadata.apiMaximum).append('\n')
        append("  }\n")
        append("}\n")
    }.toByteArray(StandardCharsets.UTF_8)

    /** Writes [metadata] below [outputRoot] and returns the generated file. */
    @JvmStatic
    fun write(outputRoot: Path, metadata: ComponentMetadata): Path {
        val target = outputRoot.resolve(ENTRY)
        Files.createDirectories(target.parent)
        Files.write(target, encode(metadata))
        return target
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }
}
