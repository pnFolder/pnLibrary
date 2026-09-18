package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigTypes
import java.lang.reflect.Type

/** Selected implementation and its serialized discriminator identity. */
internal data class ResolvedConfigType(
    val owner: String?,
    val implementation: Class<*>,
    val name: String,
    val aliases: Set<String>,
    val priority: Int,
) {
    /** Canonical discriminator emitted for [consumer]. */
    fun serializedName(consumer: String): String =
        if (owner == null || owner == consumer) name else "$owner::$name"

    /** Whether [value] may resolve this declaration for [consumer]. */
    fun matches(value: String, consumer: String): Boolean {
        val separator = value.indexOf(NAMESPACE_SEPARATOR)
        if (separator >= 0) {
            if (owner == null || !owner.equals(value.substring(0, separator), ignoreCase = true)) {
                return false
            }
            return matchesName(value.substring(separator + NAMESPACE_SEPARATOR.length))
        }
        if (owner != null && !owner.equals(consumer, ignoreCase = true)) return false
        return matchesName(value)
    }

    private fun matchesName(value: String): Boolean =
        name.equals(value, ignoreCase = true) || aliases.any { it.equals(value, ignoreCase = true) }

    private companion object {
        const val NAMESPACE_SEPARATOR = "::"
    }
}

/**
 * Resolves annotated and runtime polymorphic configuration implementations.
 *
 * Unqualified names can select built-in declarations or declarations owned by
 * the consuming plugin. Types owned by other plugins are serialized and resolved
 * with an explicit `owner::name` namespace. Priority resolves overlapping matches;
 * an equal-priority tie is rejected as ambiguous.
 */
internal class ConfigPolymorphicResolver(
    private val runtimeTypes: () -> List<RuntimeConfigType>,
    private val consumer: String,
    private val introspector: ConfigObjectIntrospector,
) {
    /** Whether [type] has enough metadata to use polymorphic conversion. */
    fun supports(type: Type, annotations: List<Annotation>): Boolean {
        val rawType = introspector.rawClass(type)
        return rawType != Any::class.java &&
            rawType.isAnnotationPresent(ConfigPolymorphic::class.java) &&
            (rawType.isAnnotationPresent(ConfigTypes::class.java) || annotations.any { it is ConfigTypes })
    }

    /** Selects the declaration written for concrete [implementation]. */
    fun encodingType(
        baseType: Class<*>,
        annotations: List<Annotation>,
        implementation: Class<*>,
    ): ResolvedConfigType = declarations(baseType, annotations)
        .filter { it.implementation == implementation }
        .maxWithOrNull(preference())
        ?: throw IllegalArgumentException(
            "Configuration implementation ${implementation.name} is not declared by ${baseType.name}",
        )

    /** Resolves one stored discriminator [value] to a concrete implementation. */
    fun decodingType(
        baseType: Class<*>,
        annotations: List<Annotation>,
        value: String,
        path: String,
    ): Class<*> {
        val available = declarations(baseType, annotations)
        val matches = available.filter { it.matches(value, consumer) }
        val selected = matches.maxWithOrNull(preference())
            ?: throw IllegalArgumentException(
                "Unknown ${baseType.simpleName} type '$value' at $path; allowed: " +
                    available.joinToString { it.name },
            )
        val equallyPreferred = matches.count {
            it.priority == selected.priority && isConsumerOwned(it) == isConsumerOwned(selected)
        }
        require(equallyPreferred == 1) {
            "Ambiguous ${baseType.simpleName} type '$value' at $path; assign different priorities"
        }
        return selected.implementation
    }

    private fun declarations(
        baseType: Class<*>,
        annotations: List<Annotation>,
    ): List<ResolvedConfigType> {
        val declared = baseType.getAnnotation(ConfigTypes::class.java)?.value?.map { type ->
            ResolvedConfigType(null, type.type.java, type.name, type.aliases.toSet(), type.priority)
        }.orEmpty()
        val fieldDeclarations = annotations.filterIsInstance<ConfigTypes>().flatMap { annotation ->
            annotation.value.map { type ->
                ResolvedConfigType(null, type.type.java, type.name, type.aliases.toSet(), type.priority)
            }
        }
        val dynamic = runtimeTypes().filter { it.baseType == baseType }.map { type ->
            ResolvedConfigType(type.owner, type.implementation, type.name, type.aliases, type.priority)
        }
        val result = declared + fieldDeclarations + dynamic
        require(result.isNotEmpty()) {
            "Polymorphic configuration type ${baseType.name} requires @ConfigTypes"
        }
        require(result.all { baseType.isAssignableFrom(it.implementation) }) {
            "Every @ConfigTypes entry on ${baseType.name} must implement that type"
        }
        return result
    }

    private fun preference(): Comparator<ResolvedConfigType> =
        compareBy<ResolvedConfigType>(::isConsumerOwned).thenBy(ResolvedConfigType::priority)

    private fun isConsumerOwned(type: ResolvedConfigType): Boolean =
        type.owner?.equals(consumer, ignoreCase = true) == true
}
