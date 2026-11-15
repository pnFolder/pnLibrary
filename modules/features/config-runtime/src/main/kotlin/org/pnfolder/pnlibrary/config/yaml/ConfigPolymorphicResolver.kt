package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import java.lang.reflect.Type

/**
 * Selected implementation and its serialized discriminator identity.
 *
 * @property owner plugin that published the type, or `null` for a type declared by annotation
 * @property implementation concrete class created for this declaration
 * @property name canonical discriminator value
 * @property aliases additional accepted discriminator values
 * @property priority tie-breaker when several declarations match
 */
internal data class ResolvedConfigType(
    val owner: String?,
    val implementation: Class<*>,
    val name: String,
    val aliases: Set<String>,
    val priority: Int,
) {
    /**
     * Discriminator written to the file. A word that reaches a file is already visible there, so it is written plainly;
     * the `owner::name` form is still accepted when reading, to tell two additions with one word apart.
     */
    @Suppress("UNUSED_PARAMETER")
    fun serializedName(consumer: String): String = name

    /** Whether [value] resolves this declaration: either the plain word or `owner::word` of its owner. */
    @Suppress("UNUSED_PARAMETER")
    fun matches(value: String, consumer: String): Boolean {
        val separator = value.indexOf(NAMESPACE_SEPARATOR)
        if (separator >= 0) {
            if (owner == null || !owner.equals(value.substring(0, separator), ignoreCase = true)) {
                return false
            }
            return matchesName(value.substring(separator + NAMESPACE_SEPARATOR.length))
        }
        return matchesName(value)
    }

    private fun matchesName(value: String): Boolean =
        name.equals(value, ignoreCase = true) || aliases.any { it.equals(value, ignoreCase = true) }

    private companion object {
        const val NAMESPACE_SEPARATOR = "::"
    }
}

/** A `type` word that no visible declaration accepts. A list can skip such an element instead of failing. */
internal class UnknownConfigTypeException(message: String) : IllegalArgumentException(message)

/**
 * Resolves annotated and runtime polymorphic configuration implementations.
 *
 * The runtime types passed in are already limited to those that work in this file, so every one of them can be
 * written with its plain word; `owner::name` also works and tells two additions with one word apart.
 * The consumer's own and built-in declarations win first, then priority;
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

    /** The declaration used to write an [implementation] of [baseType]. */
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

    /** The implementation class selected by discriminator [value] found at [path]; fails when unknown or ambiguous. */
    fun decodingType(
        baseType: Class<*>,
        annotations: List<Annotation>,
        value: String,
        path: String,
    ): Class<*> {
        val available = declarations(baseType, annotations)
        val matches = available.filter { it.matches(value, consumer) }
        val selected = matches.maxWithOrNull(preference())
            ?: throw UnknownConfigTypeException(
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
