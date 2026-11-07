package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.BackupDatabase
import org.pnfolder.pnlibrary.api.config.BackupTable
import org.pnfolder.pnlibrary.api.config.ConfigIgnore
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy
import org.pnfolder.pnlibrary.api.config.ConfigOrder
import org.pnfolder.pnlibrary.api.config.ConfigSecret
import org.pnfolder.pnlibrary.api.config.DatabaseField
import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.LinkedList
import java.util.Queue
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet

/**
 * Central reflection policy for annotated mutable configuration objects.
 *
 * Field discovery, key naming, raw generic-type resolution, object construction,
 * and collection materialization live here so every encode/decode/schema path uses
 * identical reflection rules.
 *
 * @param defaultNaming naming strategy applied to fields without an explicit key
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

    fun key(field: Field): String = field.getAnnotation(ConfigKey::class.java)
        ?.value
        ?.takeIf(String::isNotBlank)
        ?: applyNaming(field.name, defaultNaming)

    /** Returns the dotted YAML paths of every `@ConfigSecret` field of [type], including those of nested sections. */
    fun secretPaths(type: Class<*>): List<String> = buildList { collectSecrets(type, "", 0, this) }

    private fun collectSecrets(type: Class<*>, prefix: String, depth: Int, out: MutableList<String>) {
        if (depth >= 8) return
        fields(type).forEach { field ->
            val path = prefix + key(field)
            if (field.isAnnotationPresent(ConfigSecret::class.java)) {
                out += path
                return@forEach
            }
            val raw = rawClass(field.genericType)
            val nested = !raw.isPrimitive && !raw.isEnum && !raw.isArray && !isScalar(raw) &&
                !Collection::class.java.isAssignableFrom(raw) && !Map::class.java.isAssignableFrom(raw) &&
                !raw.name.startsWith("java.") && !raw.name.startsWith("kotlin.")
            if (nested) collectSecrets(raw, "$path.", depth + 1, out)
        }
    }

    /** The sections marked `@BackupDatabase` in [type], with the dotted YAML path of each. */
    fun databasePaths(type: Class<*>): List<DatabasePath> {
        val root = type.getAnnotation(BackupDatabase::class.java)?.let {
            DatabasePath("", it.kind, it.name, roleKeys(type), it.tables.toList(), tableKeys(type))
        }
        val sections = fields(type).mapNotNull { field ->
            field.getAnnotation(BackupDatabase::class.java)?.let {
                val section = rawClass(field.genericType)
                DatabasePath(key(field), it.kind, it.name, roleKeys(section), it.tables.toList(), tableKeys(section))
            }
        }
        return listOfNotNull(root) + sections
    }

    /** The YAML key of each field that says which part of a connection it holds, whatever the field is called. */
    private fun roleKeys(section: Class<*>): Map<DatabaseRole, String> = fields(section).mapNotNull { field ->
        field.getAnnotation(DatabaseField::class.java)?.let { it.role to key(field) }
    }.toMap()

    /** The YAML keys of the fields marked `@BackupTable` in [section]. */
    private fun tableKeys(section: Class<*>): List<String> = fields(section).filter { it.isAnnotationPresent(BackupTable::class.java) }.map(::key)

    fun read(field: Field, owner: Any): Any? = field.get(owner)

    /** Resolves the concrete raw class represented by [type], or [Any] if unknown. */
    fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*> ?: Any::class.java
        is WildcardType -> type.upperBounds.firstOrNull()?.let(::rawClass) ?: Any::class.java
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

    @Suppress("UNCHECKED_CAST")
    fun collection(type: Class<*>, values: Collection<Any?>, path: String): Collection<Any?> {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) return when {
            SortedSet::class.java.isAssignableFrom(type) -> TreeSet(values)
            Set::class.java.isAssignableFrom(type) -> LinkedHashSet(values)
            Queue::class.java.isAssignableFrom(type) -> LinkedList(values)
            else -> ArrayList(values)
        }
        val collection = instantiate(type, path) as? MutableCollection<Any?>
            ?: throw IllegalArgumentException("Type ${type.name} at $path is not a mutable collection")
        collection.addAll(values)
        return collection
    }

    @Suppress("UNCHECKED_CAST")
    fun map(type: Class<*>, values: Map<Any?, Any?>, path: String): Map<Any?, Any?> {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
            return if (SortedMap::class.java.isAssignableFrom(type)) {
                TreeMap(values)
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

/** A section holding database connection details: where it is in the file, what kind it is and what it is called. */
class DatabasePath(
    val path: String,
    val kind: DatabaseKind,
    val name: String,
    val keys: Map<DatabaseRole, String> = emptyMap(),
    val tables: List<String> = emptyList(),
    val tableKeys: List<String> = emptyList(),
)
