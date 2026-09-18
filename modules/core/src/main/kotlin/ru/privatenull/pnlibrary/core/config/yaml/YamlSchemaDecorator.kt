package ru.privatenull.pnlibrary.core.config.yaml

/** Metadata used to place documentation around one generated YAML field. */
internal data class YamlFieldMetadata(
    val comments: List<String>,
    val separateWithBlankLine: Boolean,
    val children: Map<String, YamlFieldMetadata>,
)

/**
 * Inserts schema comments into block-style YAML without changing scalar values.
 *
 * Metadata is selected by indentation and field name. Nested schemas are active
 * only while the source remains inside their parent indentation, preventing equal
 * keys in unrelated sections from inheriting the wrong documentation.
 */
internal object YamlSchemaDecorator {
    /** Returns [source] with comments from [root] and exactly one trailing newline. */
    fun decorate(source: String, root: Map<String, YamlFieldMetadata>): String {
        val result = mutableListOf<String>()
        val schemasByIndent = mutableMapOf(0 to root)
        source.lineSequence().forEach { line ->
            val indent = line.takeWhile { it == ' ' }.length
            schemasByIndent.keys
                .filter { it > indent }
                .toList()
                .forEach(schemasByIndent::remove)
            val key = line.trimStart().substringBefore(':').trim('"', '\'')
            val metadata = schemasByIndent[indent]?.get(key)
            if (metadata != null) {
                if (metadata.separateWithBlankLine && result.lastOrNull()?.isNotBlank() == true) {
                    result += ""
                }
                metadata.comments.forEach { comment ->
                    result += " ".repeat(indent) + "# " + comment
                }
                if (metadata.children.isNotEmpty()) {
                    schemasByIndent[indent + YAML_INDENT] = metadata.children
                } else {
                    schemasByIndent.remove(indent + YAML_INDENT)
                }
            }
            result += line
        }
        return result.joinToString("\n").trimEnd() + "\n"
    }

    private const val YAML_INDENT = 2
}
