package ru.privatenull.pnlibrary.core.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import ru.privatenull.pnlibrary.api.config.*
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.Array as ReflectArray
import java.util.function.Supplier

/**
 * Runtime extension of a polymorphic configuration type registry.
 *
 * @property owner plugin namespace that registered the implementation
 * @property baseType annotated polymorphic contract
 * @property implementation concrete no-argument implementation
 * @property name canonical discriminator value
 * @property aliases additional accepted discriminator values
 * @property priority tie-breaker when compatible declarations overlap
 */
internal data class RuntimeConfigType(
    val owner: String,
    val baseType: Class<*>,
    val implementation: Class<*>,
    val name: String,
    val aliases: Set<String>,
    val priority: Int,
)

/**
 * Reflection-based YAML codec for mutable Java/Kotlin configuration objects.
 *
 * Decoding starts from a fresh [defaults] instance, preserving field defaults for
 * absent YAML keys. Fields may use custom serializers, validation annotations,
 * naming policies, generated comments, and polymorphic implementations. Static,
 * transient, synthetic, and [ConfigIgnore] fields are excluded.
 *
 * Configuration classes must expose mutable backing fields. Nested concrete
 * objects and annotation serializers require a no-argument constructor unless a
 * registered [ConfigSerializer] handles the type.
 *
 * @param type root configuration class
 * @param defaults factory for a pristine root object on every decode
 * @param serializers serializers keyed by supported Java type
 * @param runtimeTypes current external polymorphic type registrations
 * @param consumer plugin namespace resolving unqualified external types
 * @param options naming and document-generation policy
 * @param warning receiver for values replaced via [ConfigDefaultOnInvalid]
 */
internal class AnnotatedYamlCodec<T : Any>(
    private val type: Class<T>,
    private val defaults: Supplier<T>,
    private val serializers: Map<Class<*>, ConfigSerializer<*>>,
    private val runtimeTypes: () -> List<RuntimeConfigType>,
    private val consumer: String,
    private val options: ConfigOptions,
    private val warning: (String) -> Unit,
) : ConfigCodec<T>, ConfigSchema {
    private val introspector = ConfigObjectIntrospector(options.naming)
    private val serializerResolver = ConfigSerializerResolver(serializers)
    private val schemaInspector = ConfigSchemaInspector(introspector, serializerResolver)
    private val polymorphicResolver = ConfigPolymorphicResolver(runtimeTypes, consumer, introspector)
    override val requiredPaths: Set<String> = schemaInspector.requiredPaths(type)
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        indicatorIndent = 0
        width = 120
    })

    /** Serializes [value] and decorates generated YAML with schema comments. */
    override fun encode(value: T): String {
        val raw = yaml.dump(toYamlValue(value, type, "", emptyList(), value))
        val body = YamlSchemaDecorator.decorate(raw, schemaInspector.metadata(type, value))
        val header = type.getAnnotation(ConfigComment::class.java)?.value.orEmpty()
        return if (header.isEmpty()) body else header.joinToString("\n") { "# $it" } + "\n" + body
    }

    /**
     * Decodes [yaml] into a fresh defaults instance.
     *
     * @throws IllegalArgumentException for incompatible values, missing or ambiguous
     * polymorphic types, and classes that cannot be instantiated
     */
    override fun decode(yaml: String): T {
        val loaded = this.yaml.load<Any?>(yaml) ?: linkedMapOf<String, Any?>()
        serializerResolver.forType(type)?.let {
            @Suppress("UNCHECKED_CAST")
            return it.deserialize(loaded, context(type, "", emptyList(), defaults.get())) as T
        }
        require(loaded is Map<*, *>) { "Root value of ${type.name} must be a YAML object" }
        return defaults.get().also { populate(it, type, loaded, "") }
    }

    /** Evaluates declarative validation annotations without mutating [value]. */
    fun validate(value: T): List<ConfigProblem> {
        return schemaInspector.validate(value, type)
    }

    private fun toYamlValue(
        value: Any?, declaredType: Type = value?.javaClass ?: Any::class.java, path: String = "",
        annotations: List<Annotation> = emptyList(), defaultValue: Any? = value,
    ): Any? {
        if (value != null) serializerResolver.forType(value.javaClass)?.let {
            return toYamlValue(it.serializeUntyped(value, context(declaredType, path, annotations, defaultValue)))
        }
        if (value != null && polymorphicResolver.supports(declaredType, annotations)) {
            val base = introspector.rawClass(declaredType)
            val polymorphic = base.getAnnotation(ConfigPolymorphic::class.java)
            val selected = polymorphicResolver.encodingType(base, annotations, value.javaClass)
            val storedName = selected.serializedName(consumer)
            return linkedMapOf<String, Any?>(polymorphic.discriminator to storedName).apply {
                putAll(objectYaml(value, path))
            }
        }
        return when (value) {
        null, is String, is Number, is Boolean -> value
        is Enum<*> -> value.name
        is Iterable<*> -> {
            val elementType = (declaredType as? ParameterizedType)?.actualTypeArguments?.getOrNull(0) ?: Any::class.java
            value.mapIndexed { index, item -> toYamlValue(item, elementType, "$path[$index]", annotations) }
        }
        is Array<*> -> value.mapIndexed { index, item -> toYamlValue(item, path = "$path[$index]") }
        is Map<*, *> -> linkedMapOf<Any?, Any?>().also { out -> value.forEach { (k, v) ->
            out[toYamlValue(k)] = toYamlValue(v, path = "$path.$k")
        } }
        else -> objectYaml(value, path)
        }
    }

    private fun objectYaml(value: Any, path: String): Map<String, Any?> = linkedMapOf<String, Any?>().also { out ->
        introspector.fields(value.javaClass).forEach { field ->
            val fieldValue = introspector.read(field, value)
            val serializer = serializerResolver.forField(field)
            val fieldKey = introspector.key(field)
            val fieldPath = if (path.isEmpty()) fieldKey else "$path.$fieldKey"
            val fieldContext = context(field.genericType, fieldPath, field.annotations.toList(), fieldValue)
            out[fieldKey] = if (fieldValue != null && serializer != null)
                toYamlValue(serializer.serializeUntyped(fieldValue, fieldContext))
            else toYamlValue(fieldValue, field.genericType, fieldPath, field.annotations.toList(), fieldValue)
        }
    }

    private fun populate(target: Any, targetType: Class<*>, values: Map<*, *>, prefix: String) {
        val byKey = values.entries.associate { it.key.toString() to it.value }
        introspector.fields(targetType).forEach { field ->
            val fieldKey = introspector.key(field)
            if (!byKey.containsKey(fieldKey)) return@forEach
            val raw = byKey[fieldKey]
            val path = if (prefix.isEmpty()) fieldKey else "$prefix.$fieldKey"
            val current = introspector.read(field, target)
            val converted = try {
                val resolved = enumAlias(field.type, raw, path)
                serializerResolver.forField(field)?.deserialize(
                    resolved,
                    context(field.genericType, path, field.annotations.toList(), current),
                )
                    ?: convert(resolved, field.genericType, current, path, field.annotations.toList())
            } catch (error: Exception) {
                if (field.isAnnotationPresent(ConfigDefaultOnInvalid::class.java)) {
                    warning("Invalid value at $path (${raw ?: "null"}); using default ${current ?: "null"}")
                    current
                } else {
                    val detail = if (field.type.isEnum) {
                        val allowed = schemaInspector.enumDescription(field.type)
                        " Allowed values: $allowed. Default: ${current ?: "null"}."
                    } else ""
                    throw IllegalArgumentException("Invalid configuration value at $path: ${raw ?: "null"}.$detail", error)
                }
            }
            field.set(target, converted)
        }
    }

    private fun convert(
        value: Any?, targetType: Type, current: Any? = null, path: String = "",
        annotations: List<Annotation> = emptyList(),
    ): Any? {
        val rawType = introspector.rawClass(targetType)
        serializerResolver.forType(rawType)?.let {
            return it.deserialize(value, context(targetType, path, emptyList(), current))
        }
        if (value == null) return null
        if (polymorphicResolver.supports(targetType, annotations)) {
            return polymorphic(value, rawType, path, annotations)
        }
        if (rawType == String::class.java) return value.toString()
        if (rawType == java.lang.Boolean.TYPE || rawType == java.lang.Boolean::class.java) return value as Boolean
        if (rawType == java.lang.Byte.TYPE || rawType == java.lang.Byte::class.java) return (value as Number).toByte()
        if (rawType == java.lang.Short.TYPE || rawType == java.lang.Short::class.java) return (value as Number).toShort()
        if (rawType == java.lang.Integer.TYPE || rawType == java.lang.Integer::class.java) return (value as Number).toInt()
        if (rawType == java.lang.Long.TYPE || rawType == java.lang.Long::class.java) return (value as Number).toLong()
        if (rawType == java.lang.Float.TYPE || rawType == java.lang.Float::class.java) return (value as Number).toFloat()
        if (rawType == java.lang.Double.TYPE || rawType == java.lang.Double::class.java) return (value as Number).toDouble()
        if (rawType.isEnum) return rawType.enumConstants.firstOrNull { (it as Enum<*>).name.equals(value.toString(), true) }
            ?: throw IllegalArgumentException("Unknown ${rawType.simpleName} value: $value")
        if (rawType.isArray && value is List<*>) {
            val component = rawType.componentType
            return ReflectArray.newInstance(component, value.size).also { array ->
                value.forEachIndexed { index, item -> ReflectArray.set(array, index, convert(item, component, path = "$path[$index]")) }
            }
        }
        if (Collection::class.java.isAssignableFrom(rawType) && value is List<*>) {
            val elementType = (targetType as? ParameterizedType)?.actualTypeArguments?.getOrNull(0) ?: Any::class.java
            val converted = value.mapIndexed { index, item ->
                convert(item, elementType, path = "$path[$index]", annotations = annotations)
            }
            return introspector.collection(rawType, converted, path)
        }
        if (Map::class.java.isAssignableFrom(rawType) && value is Map<*, *>) {
            val arguments = (targetType as? ParameterizedType)?.actualTypeArguments
            val keyType = arguments?.getOrNull(0) ?: String::class.java
            val valueType = arguments?.getOrNull(1) ?: Any::class.java
            val converted = linkedMapOf<Any?, Any?>().also { out ->
                value.forEach { (key, item) -> out[convert(key, keyType)] = convert(item, valueType, path = "$path.$key") }
            }
            return introspector.map(rawType, converted, path)
        }
        if (value is Map<*, *>) {
            require(!rawType.isInterface && !Modifier.isAbstract(rawType.modifiers)) {
                "Type ${rawType.name} at $path is abstract; register a ConfigSerializer"
            }
            val nested = current ?: introspector.instantiate(rawType, path)
            populate(nested, rawType, value, path)
            return nested
        }
        return value
    }

    private fun polymorphic(value: Any, baseType: Class<*>, path: String, annotations: List<Annotation>): Any {
        require(value is Map<*, *>) { "Polymorphic value at $path must be a YAML object" }
        val discriminator = baseType.getAnnotation(ConfigPolymorphic::class.java).discriminator
        val entry = value.entries.firstOrNull { it.key?.toString()?.equals(discriminator, true) == true }
            ?: error("Missing '$discriminator' for ${baseType.simpleName} at $path")
        val name = entry.value?.toString()?.trim().orEmpty()
        val implementation = polymorphicResolver.decodingType(baseType, annotations, name, path)
        val body = linkedMapOf<Any?, Any?>().also { result ->
            value.forEach { (key, item) -> if (key != entry.key) result[key] = item }
        }
        return introspector.instantiate(implementation, path).also { populate(it, implementation, body, path) }
    }

    private fun enumAlias(type: Class<*>, value: Any?, path: String): Any? {
        if (!type.isEnum || value !is String) return value
        val matches = type.enumConstants.map { it as Enum<*> }.filter { constant ->
            type.getField(constant.name).getAnnotation(ConfigAlias::class.java)
                ?.value.orEmpty().any { it.equals(value, ignoreCase = true) }
        }
        require(matches.size <= 1) { "Enum alias '$value' is ambiguous at $path in ${type.name}" }
        return matches.singleOrNull()?.name ?: value
    }

    private fun context(
        declaredType: Type, path: String, annotations: List<Annotation>, defaultValue: Any?,
    ) = ConfigSerializationContext(path, declaredType, introspector.rawClass(declaredType), annotations, defaultValue)

    private fun ConfigSerializer<Any>.serializeUntyped(value: Any, context: ConfigSerializationContext): Any? =
        serialize(value, context)
}
