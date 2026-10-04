package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigAlias
import ru.privatenull.pnlibrary.api.config.ConfigComment
import ru.privatenull.pnlibrary.api.config.ConfigNewLine
import ru.privatenull.pnlibrary.api.config.ConfigNotBlank
import ru.privatenull.pnlibrary.api.config.ConfigPattern
import ru.privatenull.pnlibrary.api.config.ConfigProblem
import ru.privatenull.pnlibrary.api.config.ConfigRange
import ru.privatenull.pnlibrary.api.config.ConfigRequired
import java.lang.reflect.Field

/**
 * Describes and validates the declarative schema of configuration objects.
 *
 * This class contains no YAML parsing logic. It only interprets field annotations
 * and follows nested configuration objects that are not handled by serializers.
 */
internal class ConfigSchemaInspector(
    private val introspector: ConfigObjectIntrospector,
    private val serializers: ConfigSerializerResolver,
) {
    /** Returns every required YAML path reachable from [rootType]. */
    fun requiredPaths(rootType: Class<*>): Set<String> = requiredPaths(rootType, "")

    /** Builds comment and layout metadata used when rendering generated YAML. */
    fun metadata(type: Class<*>, instance: Any?): Map<String, YamlFieldMetadata> =
        introspector.fields(type).associate { field ->
            val value = instance?.let { owner -> introspector.read(field, owner) }
            val key = introspector.key(field)
            val comments = field.getAnnotation(ConfigComment::class.java)?.value?.toList().orEmpty()
            val enumHelp = if (field.type.isEnum) {
                listOf("Allowed values: ${enumDescription(field.type)}. Default: ${value ?: "null"}.")
            } else {
                emptyList()
            }

            key to YamlFieldMetadata(
                comments = comments + enumHelp,
                separateWithBlankLine = field.isAnnotationPresent(ConfigNewLine::class.java),
                children = if (isNestedObject(field)) metadata(field.type, value) else emptyMap(),
            )
        }

    /** Evaluates all validation annotations reachable from [value]. */
    fun validate(value: Any?, type: Class<*>): List<ConfigProblem> = buildList {
        validateObject(value, type, "", this)
    }

    /** Human-readable enum constants and aliases for error messages. */
    fun enumDescription(type: Class<*>): String = type.enumConstants.joinToString { raw ->
        val constant = raw as Enum<*>
        val aliases = type.getField(constant.name)
            .getAnnotation(ConfigAlias::class.java)
            ?.value
            .orEmpty()
        if (aliases.isEmpty()) constant.name else "${constant.name} (aliases: ${aliases.joinToString()})"
    }

    private fun requiredPaths(type: Class<*>, prefix: String): Set<String> {
        if (serializers.forType(type) != null) return emptySet()

        return buildSet {
            introspector.fields(type).forEach { field ->
                val key = introspector.key(field)
                val path = nestedPath(prefix, key)
                if (field.isAnnotationPresent(ConfigRequired::class.java)) add(path)
                if (isNestedObject(field)) addAll(requiredPaths(field.type, path))
            }
        }
    }

    private fun validateObject(
        value: Any?,
        type: Class<*>,
        prefix: String,
        problems: MutableList<ConfigProblem>,
    ) {
        if (value == null) return

        introspector.fields(type).forEach { field ->
            val current = introspector.read(field, value)
            val path = nestedPath(prefix, introspector.key(field))

            field.getAnnotation(ConfigRange::class.java)?.let { range ->
                val number = current as? Number
                if (number == null || number.toDouble() !in range.min..range.max) {
                    problems += ConfigProblem(path, "must be between ${range.min} and ${range.max}")
                }
            }
            if (field.isAnnotationPresent(ConfigNotBlank::class.java) &&
                (current !is String || current.isBlank())
            ) {
                problems += ConfigProblem(path, "must not be blank")
            }
            field.getAnnotation(ConfigPattern::class.java)?.let { pattern ->
                if (current !is String || !Regex(pattern.value).matches(current)) {
                    problems += ConfigProblem(path, "must match ${pattern.value}")
                }
            }
            if (current != null && isNestedObject(field)) {
                validateObject(current, field.type, path, problems)
            }
        }
    }

    private fun isNestedObject(field: Field): Boolean =
        serializers.forField(field) == null &&
            serializers.forType(field.type) == null &&
            !introspector.isScalar(field.type) &&
            !Collection::class.java.isAssignableFrom(field.type) &&
            !Map::class.java.isAssignableFrom(field.type) &&
            !field.type.isArray

    private fun nestedPath(prefix: String, key: String): String =
        if (prefix.isEmpty()) key else "$prefix.$key"
}
