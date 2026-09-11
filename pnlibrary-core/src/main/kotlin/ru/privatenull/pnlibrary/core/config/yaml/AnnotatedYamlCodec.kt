package ru.privatenull.pnlibrary.core.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import ru.privatenull.pnlibrary.api.config.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.Array as ReflectArray
import java.util.function.Supplier

/** Reflection codec for ordinary mutable Java classes and Kotlin classes with backing fields. */
internal class AnnotatedYamlCodec<T : Any>(
    private val type: Class<T>,
    private val defaults: Supplier<T>,
    private val serializers: Map<Class<*>, ConfigSerializer<*>>,
    knownConfigurationTypes: Set<Class<*>>,
    private val options: ConfigOptions,
    private val warning: (String) -> Unit,
) : ConfigCodec<T>, ConfigSchema {
    private val configurationTypes = knownConfigurationTypes + discoverTypes(defaults.get())
    private val annotationSerializers = mutableMapOf<Class<out ConfigSerializer<*>>, ConfigSerializer<*>>()
    override val requiredPaths: Set<String> = required(type)
    private val yaml = Yaml(DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
        indicatorIndent = 0
        width = 120
    })

    override fun encode(value: T): String {
        val raw = yaml.dump(toYamlValue(value, type, "", emptyList(), value))
        val body = decorate(raw, schema(type, value))
        val header = type.getAnnotation(ConfigComment::class.java)?.value.orEmpty()
        return if (header.isEmpty()) body else header.joinToString("\n") { "# $it" } + "\n" + body
    }

    override fun decode(yaml: String): T {
        val loaded = this.yaml.load<Any?>(yaml) ?: linkedMapOf<String, Any?>()
        serializer(type)?.let {
            @Suppress("UNCHECKED_CAST")
            return it.deserialize(loaded, context(type, "", emptyList(), defaults.get())) as T
        }
        require(loaded is Map<*, *>) { "Root value of ${type.name} must be a YAML object" }
        return defaults.get().also { populate(it, type, loaded, "") }
    }

    fun validate(value: T): List<ConfigProblem> {
        val problems = mutableListOf<ConfigProblem>()
        validateObject(value, type, "", problems)
        return problems
    }

    private fun toYamlValue(
        value: Any?, declaredType: Type = value?.javaClass ?: Any::class.java, path: String = "",
        annotations: List<Annotation> = emptyList(), defaultValue: Any? = value,
    ): Any? {
        if (value != null) serializer(value.javaClass)?.let {
            return toYamlValue(it.serializeUntyped(value, context(declaredType, path, annotations, defaultValue)))
        }
        if (value != null && isPolymorphic(declaredType)) {
            val annotation = value.javaClass.getAnnotation(ConfigType::class.java)
                ?: error("Configuration implementation ${value.javaClass.name} requires @ConfigType")
            return linkedMapOf(annotation.value to objectYaml(value, path))
        }
        return when (value) {
        null, is String, is Number, is Boolean -> value
        is Enum<*> -> value.name
        is Iterable<*> -> {
            val elementType = (declaredType as? ParameterizedType)?.actualTypeArguments?.getOrNull(0) ?: Any::class.java
            value.mapIndexed { index, item -> toYamlValue(item, elementType, "$path[$index]") }
        }
        is Array<*> -> value.mapIndexed { index, item -> toYamlValue(item, path = "$path[$index]") }
        is Map<*, *> -> linkedMapOf<Any?, Any?>().also { out -> value.forEach { (k, v) ->
            out[toYamlValue(k)] = toYamlValue(v, path = "$path.$k")
        } }
        else -> objectYaml(value, path)
        }
    }

    private fun objectYaml(value: Any, path: String): Map<String, Any?> = linkedMapOf<String, Any?>().also { out ->
        fields(value.javaClass).forEach { field ->
            val fieldValue = read(field, value)
            val serializer = serializer(field)
            val fieldPath = if (path.isEmpty()) key(field) else "$path.${key(field)}"
            val fieldContext = context(field.genericType, fieldPath, field.annotations.toList(), fieldValue)
            out[key(field)] = if (fieldValue != null && serializer != null)
                toYamlValue(serializer.serializeUntyped(fieldValue, fieldContext))
            else toYamlValue(fieldValue, field.genericType, fieldPath, field.annotations.toList(), fieldValue)
        }
    }

    private fun populate(target: Any, targetType: Class<*>, values: Map<*, *>, prefix: String) {
        val byKey = values.entries.associate { it.key.toString() to it.value }
        fields(targetType).forEach { field ->
            if (!byKey.containsKey(key(field))) return@forEach
            val raw = byKey[key(field)]
            val path = if (prefix.isEmpty()) key(field) else "$prefix.${key(field)}"
            val current = read(field, target)
            val converted = try {
                val resolved = enumAlias(field.type, raw, path)
                serializer(field)?.deserialize(resolved, context(field.genericType, path, field.annotations.toList(), current))
                    ?: convert(resolved, field.genericType, current, path)
            } catch (error: Throwable) {
                if (field.isAnnotationPresent(ConfigDefaultOnInvalid::class.java)) {
                    warning("Invalid value at $path (${raw ?: "null"}); using default ${current ?: "null"}")
                    current
                } else {
                    val detail = if (field.type.isEnum) {
                        val allowed = enumDescription(field.type)
                        " Allowed values: $allowed. Default: ${current ?: "null"}."
                    } else ""
                    throw IllegalArgumentException("Invalid configuration value at $path: ${raw ?: "null"}.$detail", error)
                }
            }
            field.set(target, converted)
        }
    }

    private fun convert(value: Any?, targetType: Type, current: Any? = null, path: String = ""): Any? {
        val rawType = rawClass(targetType)
        serializer(rawType)?.let { return it.deserialize(value, context(targetType, path, emptyList(), current)) }
        if (value == null) return null
        if (isPolymorphic(targetType)) return polymorphic(value, rawType, path)
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
            val converted = value.mapIndexed { index, item -> convert(item, elementType, path = "$path[$index]") }
            return newCollection(rawType, converted, path)
        }
        if (Map::class.java.isAssignableFrom(rawType) && value is Map<*, *>) {
            val arguments = (targetType as? ParameterizedType)?.actualTypeArguments
            val keyType = arguments?.getOrNull(0) ?: String::class.java
            val valueType = arguments?.getOrNull(1) ?: Any::class.java
            val converted = linkedMapOf<Any?, Any?>().also { out ->
                value.forEach { (key, item) -> out[convert(key, keyType)] = convert(item, valueType, path = "$path.$key") }
            }
            return newMap(rawType, converted, path)
        }
        if (value is Map<*, *>) {
            require(!rawType.isInterface && !Modifier.isAbstract(rawType.modifiers)) {
                "Type ${rawType.name} at $path is abstract; register a ConfigSerializer"
            }
            val nested = current ?: instantiate(rawType, path)
            populate(nested, rawType, value, path)
            return nested
        }
        return value
    }

    private fun polymorphic(value: Any, baseType: Class<*>, path: String): Any {
        require(value is Map<*, *> && value.size == 1) {
            "Polymorphic value at $path must contain exactly one type"
        }
        val entry = value.entries.single()
        val name = entry.key?.toString()?.trim().orEmpty()
        val matches = configurationTypes.filter {
            baseType.isAssignableFrom(it) &&
                it.getAnnotation(ConfigType::class.java)?.value?.equals(name, true) == true
        }
        require(matches.size <= 1) { "Ambiguous ${baseType.simpleName} type '$name' at $path" }
        val implementation = matches.singleOrNull()
            ?: error("Unknown ${baseType.simpleName} type '$name' at $path")
        require(entry.value is Map<*, *>) { "Configuration type '$name' at $path must contain an object" }
        return instantiate(implementation, path).also { populate(it, implementation, entry.value as Map<*, *>, path) }
    }

    private fun isPolymorphic(type: Type): Boolean {
        val raw = rawClass(type)
        return raw != Any::class.java && (raw.isInterface || Modifier.isAbstract(raw.modifiers)) &&
            configurationTypes.any(raw::isAssignableFrom)
    }

    private fun discoverTypes(root: Any): Set<Class<*>> {
        val result = linkedSetOf<Class<*>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())

        fun visit(value: Any?) {
            if (value == null || isScalar(value.javaClass) || !visited.add(value)) return
            value.javaClass.getAnnotation(ConfigType::class.java)?.let {
                require(it.value.isNotBlank()) { "@ConfigType value on ${value.javaClass.name} must not be blank" }
                result += value.javaClass
            }
            if (serializer(value.javaClass) != null) return
            when (value) {
                is Iterable<*> -> value.forEach(::visit)
                is Map<*, *> -> value.forEach { (key, item) -> visit(key); visit(item) }
                is Array<*> -> value.forEach(::visit)
                else -> fields(value.javaClass).forEach { visit(read(it, value)) }
            }
        }

        visit(root)
        return result
    }

    private fun schema(target: Class<*>, instance: Any?): Map<String, Meta> = fields(target).associate { field ->
        val fieldValue = instance?.let { read(field, it) }
        val explicit = field.getAnnotation(ConfigComment::class.java)?.value?.toList().orEmpty()
        val enumHelp = if (field.type.isEnum) listOf(
            "Allowed values: ${enumDescription(field.type)}. Default: ${fieldValue ?: "null"}."
        ) else emptyList()
        key(field) to Meta(
            explicit + enumHelp,
            field.isAnnotationPresent(ConfigNewLine::class.java),
            if (isStructured(field)) schema(field.type, fieldValue) else emptyMap(),
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
            if (current != null && isStructured(field))
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

    private fun key(field: Field): String = field.getAnnotation(ConfigKey::class.java)?.value?.takeIf(String::isNotBlank)
        ?: name(field.name, field.declaringClass.getAnnotation(ConfigNaming::class.java)?.value ?: options.naming)
    private fun name(value: String, strategy: ConfigNamingStrategy): String {
        if (strategy == ConfigNamingStrategy.AS_DECLARED) return value
        val words = value.replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "\$1 \$2")
            .split(Regex("[_\\-\\s]+"))
            .filter(String::isNotBlank)
        return when (strategy) {
            ConfigNamingStrategy.AS_DECLARED -> value
            ConfigNamingStrategy.CAMEL_CASE -> words.first().lowercase() + words.drop(1).joinToString("") {
                it.lowercase().replaceFirstChar(Char::uppercase)
            }
            ConfigNamingStrategy.SNAKE_CASE -> words.joinToString("_") { it.lowercase() }
            ConfigNamingStrategy.KEBAB_CASE -> words.joinToString("-") { it.lowercase() }
            ConfigNamingStrategy.UPPER_SNAKE_CASE -> words.joinToString("_") { it.uppercase() }
        }
    }
    private fun read(field: Field, owner: Any): Any? = field.get(owner)
    private fun isScalar(type: Class<*>) = type.isPrimitive || type.isEnum || type == String::class.java || Number::class.java.isAssignableFrom(type) || type == java.lang.Boolean::class.java
    private fun rawClass(type: Type): Class<*> = when (type) {
        is Class<*> -> type
        is ParameterizedType -> type.rawType as Class<*>
        else -> Any::class.java
    }

    @Suppress("UNCHECKED_CAST")
    private fun newCollection(type: Class<*>, values: Collection<Any?>, path: String): Collection<Any?> {
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

    @Suppress("UNCHECKED_CAST")
    private fun newMap(type: Class<*>, values: Map<Any?, Any?>, path: String): Map<Any?, Any?> {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) return if (
            java.util.SortedMap::class.java.isAssignableFrom(type)
        ) java.util.TreeMap(values) else LinkedHashMap(values)
        val map = instantiate(type, path) as? MutableMap<Any?, Any?>
            ?: throw IllegalArgumentException("Type ${type.name} at $path is not a mutable map")
        map.putAll(values)
        return map
    }

    private fun instantiate(type: Class<*>, path: String): Any = try {
        type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
    } catch (error: Throwable) {
        throw IllegalArgumentException(
            "Type ${type.name} at $path needs a no-argument constructor or a ConfigSerializer",
            error,
        )
    }
    @Suppress("UNCHECKED_CAST")
    private fun serializer(type: Class<*>): ConfigSerializer<Any>? =
        (annotatedSerializer(type) ?: serializers[type] ?: serializers.entries.firstOrNull { it.key.isAssignableFrom(type) }?.value) as? ConfigSerializer<Any>

    @Suppress("UNCHECKED_CAST")
    private fun serializer(field: Field): ConfigSerializer<Any>? =
        field.getAnnotation(ConfigSerializeWith::class.java)?.value?.java?.let(::serializerInstance) as? ConfigSerializer<Any>

    private fun annotatedSerializer(type: Class<*>): ConfigSerializer<*>? =
        type.getAnnotation(ConfigSerializeWith::class.java)?.value?.java?.let(::serializerInstance)

    private fun serializerInstance(type: Class<out ConfigSerializer<*>>): ConfigSerializer<*> =
        synchronized(annotationSerializers) {
            annotationSerializers.getOrPut(type) {
                type.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
            }
        }

    private fun required(target: Class<*>, prefix: String = ""): Set<String> {
        if (serializer(target) != null) return emptySet()
        return buildSet {
        fields(target).forEach { field ->
            val path = if (prefix.isEmpty()) key(field) else "$prefix.${key(field)}"
            if (field.isAnnotationPresent(ConfigRequired::class.java)) add(path)
            if (isStructured(field))
                addAll(required(field.type, path))
        }
        }
    }
    private fun isStructured(field: Field): Boolean =
        serializer(field) == null && serializer(field.type) == null && !isScalar(field.type) &&
            !Collection::class.java.isAssignableFrom(field.type) && !Map::class.java.isAssignableFrom(field.type) && !field.type.isArray

    private fun enumAlias(type: Class<*>, value: Any?, path: String): Any? {
        if (!type.isEnum || value !is String) return value
        val matches = type.enumConstants.map { it as Enum<*> }.filter { constant ->
            type.getField(constant.name).getAnnotation(ConfigAlias::class.java)
                ?.value.orEmpty().any { it.equals(value, ignoreCase = true) }
        }
        require(matches.size <= 1) { "Enum alias '$value' is ambiguous at $path in ${type.name}" }
        return matches.singleOrNull()?.name ?: value
    }

    private fun enumDescription(type: Class<*>): String = type.enumConstants.joinToString { raw ->
        val constant = raw as Enum<*>
        val aliases = type.getField(constant.name).getAnnotation(ConfigAlias::class.java)?.value.orEmpty()
        if (aliases.isEmpty()) constant.name else "${constant.name} (aliases: ${aliases.joinToString()})"
    }

    private fun context(
        declaredType: Type, path: String, annotations: List<Annotation>, defaultValue: Any?,
    ) = ConfigSerializationContext(path, declaredType, rawClass(declaredType), annotations, defaultValue)

    private fun ConfigSerializer<Any>.serializeUntyped(value: Any, context: ConfigSerializationContext): Any? =
        serialize(value, context)
    private data class Meta(val comments: List<String>, val newLine: Boolean, val children: Map<String, Meta>)
}
