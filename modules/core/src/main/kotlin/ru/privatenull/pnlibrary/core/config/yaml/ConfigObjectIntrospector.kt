package ru.privatenull.pnlibrary.core.config.yaml

import ru.privatenull.pnlibrary.api.config.ConfigIgnore
import ru.privatenull.pnlibrary.api.config.ConfigKey
import ru.privatenull.pnlibrary.api.config.ConfigNaming
import ru.privatenull.pnlibrary.api.config.ConfigNamingStrategy
import ru.privatenull.pnlibrary.api.config.ConfigOrder
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Central reflection policy for annotated mutable configuration objects.
 *
 * Field discovery, key naming, raw generic-type resolution, object construction,
 * and collection materialization live here so every encode/decode/schema path uses
 * identical reflection rules.
 *
 * @param defaultNaming naming strategy used when a class has no [ConfigNaming]
 */
internal class ConfigObjectIntrospector(
    private val defaultNaming: ConfigNamingStrategy,
) {
    /** Returns serializable fields in superclass-first, configured order. */
    fun fields(type: Class<*>): List<Field> {
        val hierarchy = generateSequence(type) { it.superclass }
            .takeWhile { it != Any::class.java }
            .toList()
            .asReversed()
        return hierarchy.flatMap { it.declaredFields.asList() }
            .filterNot { field ->
                field.isSynthetic || Modifier.isStatic(field.modifiers) ||
                    Modifier.isTransient(field.modifiers) ||
                    field.isAnnotationPresent(ConfigIgnore::class.java)
            }
            .sortedWith(compareBy<Field> { it.getAnnotation(ConfigOrder::class.java)?.value ?: 0 })
            .onEach { it.isAccessible = true }
    }

    /** Resolves the serialized key for [field]. */
    fun key(field: Field): String = field.getAnnotation(ConfigKey::class.java)
        ?.value
        ?.takeIf(String::isNotBlank)
        ?: applyNaming(
            field.name,
            field.declaringClass.getAnnotation(ConfigNaming::class.java)?.value ?: defaultNaming,
        )

    /** Reads an already-discovered field from [owner]. */
    fun read(field: Field, owner: Any): Any? = field.get(owner)

    /** Resolves the concrete raw class represented by [type], or [Any] if unknown. */
    fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*> ?: Any::class.java
        else -> Any::class.java
    }

    /** Whether [type] can be represented directly as one YAML scalar. */
    fun isScalar(type: Class<*>): Boolean =
        type.isPrimitive || type.isEnum || type == String::class.java ||
            Number::class.java.isAssignableFrom(type) || type == java.lang.Boolean::class.java

    /**
     * Creates [type] through an accessible no-argument constructor.
     *
     * @throws IllegalArgumentException with configuration [path] context when the
     * type cannot be instantiated
     */
    fun instantiate(type: Class<*>, path: String): Any = try {
        type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
    } catch (exception: Exception) {
        throw IllegalArgumentException(
            "Type ${type.name} at $path needs a no-argument constructor or a ConfigSerializer",
            exception,
        )
    }

    /** Materializes converted [values] using the declared collection [type]. */
    @Suppress("UNCHECKED_CAST")
    fun collection(type: Class<*>, values: Collection<Any?>, path: String): Collection<Any?> {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) return when {
            java.util.SortedSet::class.java.isAssignableFrom(type) -> java.util.TreeSet(values)
            Set::class.java.isAssignableFrom(type) -> LinkedHashSet(values)
            java.util.Queue::class.java.isAssignableFrom(type) -> java.util.LinkedList(values)
            else -> ArrayList(values)
        }
        val collection = instantiate(type, path) as? MutableCollection<Any?>
            ?: throw IllegalArgumentException("Type ${type.name} at $path is not a mutable collection")
        collection.addAll(values)
        return collection
    }

    /** Materializes converted [values] using the declared map [type]. */
    @Suppress("UNCHECKED_CAST")
    fun map(type: Class<*>, values: Map<Any?, Any?>, path: String): Map<Any?, Any?> {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
            return if (java.util.SortedMap::class.java.isAssignableFrom(type)) {
                java.util.TreeMap(values)
            } else {
                LinkedHashMap(values)
            }
        }
        val map = instantiate(type, path) as? MutableMap<Any?, Any?>
            ?: throw IllegalArgumentException("Type ${type.name} at $path is not a mutable map")
        map.putAll(values)
        return map
    }

    private fun applyNaming(value: String, strategy: ConfigNamingStrategy): String {
        if (strategy == ConfigNamingStrategy.AS_DECLARED) return value
        val words = value.replace(LOWER_TO_UPPER_BOUNDARY, "$1 $2")
            .replace(ACRONYM_BOUNDARY, "$1 $2")
            .split(WORD_SEPARATOR)
            .filter(String::isNotBlank)
        return when (strategy) {
            ConfigNamingStrategy.AS_DECLARED -> value
            ConfigNamingStrategy.CAMEL_CASE -> words.first().lowercase() +
                words.drop(1).joinToString("") { word ->
                    word.lowercase().replaceFirstChar(Char::uppercase)
                }
            ConfigNamingStrategy.SNAKE_CASE -> words.joinToString("_") { it.lowercase() }
            ConfigNamingStrategy.KEBAB_CASE -> words.joinToString("-") { it.lowercase() }
            ConfigNamingStrategy.UPPER_SNAKE_CASE -> words.joinToString("_") { it.uppercase() }
        }
    }

    private companion object {
        val LOWER_TO_UPPER_BOUNDARY = Regex("([a-z0-9])([A-Z])")
        val ACRONYM_BOUNDARY = Regex("([A-Z]+)([A-Z][a-z])")
        val WORD_SEPARATOR = Regex("[_\\-\\s]+")
    }
}
