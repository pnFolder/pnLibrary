package ru.privatenull.pnlibrary.core.config.yaml

import com.google.gson.Gson
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import ru.privatenull.pnlibrary.api.config.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Reflection codec for ordinary mutable Java classes and Kotlin classes with backing fields. */
internal class AnnotatedYamlCodec<T : Any>(private val type: Class<T>) : ConfigCodec<T> {
    private val gson = Gson()
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        indicatorIndent = 0
        width = 120
    })

    override fun encode(value: T): String {
        val raw = yaml.dump(toYamlValue(value))
        val body = decorate(raw, schema(type))
        val header = type.getAnnotation(ConfigComment::class.java)?.value.orEmpty()
        return if (header.isEmpty()) body else header.joinToString("\n") { "# $it" } + "\n" + body
    }

    override fun decode(yaml: String): T {
        val loaded = this.yaml.load<Any?>(yaml) ?: linkedMapOf<String, Any?>()
        val normalized = fromYamlValue(loaded, type)
        return gson.fromJson(gson.toJson(normalized), type)
            ?: throw IllegalArgumentException("Unable to create ${type.name}")
    }

    fun validate(value: T): List<ConfigProblem> {
        val problems = mutableListOf<ConfigProblem>()
        validateObject(value, type, "", problems)
        return problems
    }

    private fun toYamlValue(value: Any?): Any? = when (value) {
        null, is String, is Number, is Boolean -> value
        is Enum<*> -> value.name
        is Iterable<*> -> value.map(::toYamlValue)
        is Array<*> -> value.map(::toYamlValue)
        is Map<*, *> -> linkedMapOf<Any?, Any?>().also { out -> value.forEach { (k, v) -> out[k] = toYamlValue(v) } }
        else -> linkedMapOf<String, Any?>().also { out ->
            fields(value.javaClass).forEach { field -> out[key(field)] = toYamlValue(read(field, value)) }
        }
    }

    private fun fromYamlValue(value: Any?, target: Class<*>): Any? {
        if (value !is Map<*, *>) return value
        val byKey = fields(target).associateBy(::key)
        return linkedMapOf<String, Any?>().also { out ->
            value.forEach { (rawKey, rawValue) ->
                val field = byKey[rawKey.toString()] ?: return@forEach
                out[field.name] = if (rawValue is Map<*, *> && !isScalar(field.type)) {
                    fromYamlValue(rawValue, field.type)
                } else rawValue
            }
        }
    }

    private fun schema(target: Class<*>): Map<String, Meta> = fields(target).associate { field ->
        key(field) to Meta(
            field.getAnnotation(ConfigComment::class.java)?.value?.toList().orEmpty(),
            field.isAnnotationPresent(ConfigNewLine::class.java),
            if (isScalar(field.type) || Collection::class.java.isAssignableFrom(field.type) || Map::class.java.isAssignableFrom(field.type))
                emptyMap() else schema(field.type),
        )
    }

    private fun decorate(source: String, root: Map<String, Meta>): String {
        val result = mutableListOf<String>()
        val schemas = mutableMapOf(0 to root)
        source.lineSequence().forEach { line ->
            val indent = line.takeWhile { it == ' ' }.length
            schemas.keys.filter { it > indent }.toList().forEach(schemas::remove)
            val key = line.trimStart().substringBefore(':').trim('"', '\'')
            val meta = schemas[indent]?.get(key)
            if (meta != null) {
                if (meta.newLine && result.lastOrNull()?.isNotBlank() == true) result += ""
                meta.comments.forEach { result += " ".repeat(indent) + "# " + it }
                if (meta.children.isNotEmpty()) schemas[indent + 2] = meta.children else schemas.remove(indent + 2)
            }
            result += line
        }
        return result.joinToString("\n").trimEnd() + "\n"
    }

    private fun validateObject(value: Any?, target: Class<*>, prefix: String, problems: MutableList<ConfigProblem>) {
        if (value == null) return
        fields(target).forEach { field ->
            val current = read(field, value)
            val path = if (prefix.isEmpty()) key(field) else "$prefix.${key(field)}"
            field.getAnnotation(ConfigRange::class.java)?.let { range ->
                val number = current as? Number
                if (number == null || number.toDouble() !in range.min..range.max)
                    problems += ConfigProblem(path, "must be between ${range.min} and ${range.max}")
            }
            if (field.isAnnotationPresent(ConfigNotBlank::class.java) && (current !is String || current.isBlank()))
                problems += ConfigProblem(path, "must not be blank")
            field.getAnnotation(ConfigPattern::class.java)?.let { pattern ->
                if (current !is String || !Regex(pattern.value).matches(current))
                    problems += ConfigProblem(path, "must match ${pattern.value}")
            }
            if (current != null && !isScalar(field.type) && current !is Collection<*> && current !is Map<*, *>)
                validateObject(current, field.type, path, problems)
        }
    }

    private fun fields(target: Class<*>): List<Field> {
        val hierarchy = generateSequence(target) { it.superclass }.takeWhile { it != Any::class.java }.toList().asReversed()
        return hierarchy.flatMap { it.declaredFields.asList() }
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) || Modifier.isTransient(it.modifiers) || it.isAnnotationPresent(ConfigIgnore::class.java) }
            .sortedWith(compareBy<Field> { it.getAnnotation(ConfigOrder::class.java)?.value ?: 0 })
            .onEach { it.isAccessible = true }
    }

    private fun key(field: Field) = field.getAnnotation(ConfigKey::class.java)?.value?.takeIf(String::isNotBlank) ?: field.name
    private fun read(field: Field, owner: Any): Any? = field.get(owner)
    private fun isScalar(type: Class<*>) = type.isPrimitive || type.isEnum || type == String::class.java || Number::class.java.isAssignableFrom(type) || type == java.lang.Boolean::class.java
    private data class Meta(val comments: List<String>, val newLine: Boolean, val children: Map<String, Meta>)
}
