package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigDuration
import org.pnfolder.pnlibrary.api.config.ConfigEditable
import org.pnfolder.pnlibrary.api.config.ConfigHint
import org.pnfolder.pnlibrary.api.config.ConfigStep
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigSecret
import org.pnfolder.pnlibrary.api.config.ConfigSetting
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * Finds the fields marked `@ConfigEditable` in a configuration class and changes them from typed text.
 *
 * Fields are found by looking through nested sections; a `@ConfigSecret` field and everything under it is never offered.
 * Changing a value sets the field of the loaded object; the caller validates and writes the file and calls
 * [Applied.restore] when that fails.
 */
internal class ConfigSettingsEditor(
    private val introspector: ConfigObjectIntrospector,
    rootType: Class<*>,
) {
    private class Entry(val path: String, val chain: List<Field>, val annotation: ConfigEditable, val kind: ConfigSettingKind) {
        val field: Field get() = chain.last()
    }

    /** What [set] did: the setting with its new value, the old text, and a way to put the old value back. */
    class Applied(val setting: ConfigSetting, val previous: String?, val restore: () -> Unit)

    private val entries: Map<String, Entry> = buildList { collect(rootType, "", emptyList(), 0, this) }.associateBy { it.path }

    /** Whether the class has no editable field. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /** The settings with their current values taken from [root], or without values when it is `null`. */
    fun describe(root: Any?): List<ConfigSetting> = entries.values.map { setting(it, root) }

    /** Sets the field behind [path] from [text]; nothing is written to disk here. */
    fun set(root: Any, path: String, text: String): Applied {
        val entry = entries[path] ?: throw ConfigEditException(
            "Такой настройки нет: $path." + if (entries.isEmpty()) "" else " Доступны: ${entries.keys.joinToString()}",
        )
        val target = resolve(root, entry)
        val old = entry.field.get(target)
        val parsed = parse(entry, text)
        entry.field.set(target, parsed)
        val updated = setting(entry, root)
        return Applied(updated, display(old)) { entry.field.set(target, old) }
    }

    private fun resolve(root: Any, entry: Entry): Any {
        var current = root
        for (field in entry.chain.dropLast(1)) {
            current = field.get(current) ?: throw ConfigEditException("Раздел ${field.name} пуст, значение не изменить")
        }
        return current
    }

    private fun setting(entry: Entry, root: Any?): ConfigSetting {
        val type = entry.field.type
        val range = entry.field.getAnnotation(ConfigRange::class.java)
        val value = root?.let { runCatching { display(entry.field.get(resolve(it, entry))) }.getOrNull() }
        return ConfigSetting(
            path = entry.path,
            title = entry.annotation.title.ifBlank { introspector.key(entry.field) },
            description = entry.annotation.description,
            kind = entry.kind,
            value = value,
            choices = choicesOf(entry),
            min = range?.min?.takeIf { it != -Double.MAX_VALUE },
            max = range?.max?.takeIf { it != Double.MAX_VALUE },
            apply = entry.annotation.apply,
            unit = entry.field.getAnnotation(ConfigStep::class.java)?.unit.orEmpty(),
            step = entry.field.getAnnotation(ConfigStep::class.java)?.step ?: 1.0,
            duration = entry.field.getAnnotation(ConfigDuration::class.java)?.unit,
            format = hint(entry)?.format.orEmpty(),
            examples = hint(entry)?.examples?.toList().orEmpty(),
        )
    }

    private fun hint(entry: Entry): ConfigHint? = entry.field.getAnnotation(ConfigHint::class.java)

    private fun choicesOf(entry: Entry): List<String> {
        val type = entry.field.type
        return if (type.isEnum) type.enumConstants.map { (it as Enum<*>).name } else hint(entry)?.values?.toList().orEmpty()
    }

    private fun display(value: Any?): String? = when (value) {
        null -> null
        is Enum<*> -> value.name
        else -> value.toString()
    }

    private fun parse(entry: Entry, text: String): Any {
        val type = entry.field.type
        val raw = text.trim()
        try {
            return when (entry.kind) {
                ConfigSettingKind.BOOLEAN -> when (raw.lowercase(Locale.ROOT)) {
                    "true", "on", "yes", "1", "вкл", "да" -> true
                    "false", "off", "no", "0", "выкл", "нет" -> false
                    else -> throw ConfigEditException("«$raw» не подходит: нужно true или false")
                }
                ConfigSettingKind.INTEGER -> {
                    val number = DurationText.parse(raw, entry.field.getAnnotation(ConfigDuration::class.java)?.unit) ?: raw.toLong()
                    when (type) {
                        Int::class.javaPrimitiveType, Int::class.javaObjectType -> Math.toIntExact(number)
                        Short::class.javaPrimitiveType, Short::class.javaObjectType -> number.also { require(it in Short.MIN_VALUE..Short.MAX_VALUE) }.toShort()
                        Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> number.also { require(it in Byte.MIN_VALUE..Byte.MAX_VALUE) }.toByte()
                        else -> number
                    }
                }
                ConfigSettingKind.DECIMAL -> {
                    val number = raw.replace(',', '.').toDouble()
                    require(number.isFinite())
                    if (type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType) number.toFloat() else number
                }
                ConfigSettingKind.TEXT -> text
                ConfigSettingKind.CHOICE -> if (type.isEnum) {
                    type.enumConstants.firstOrNull { (it as Enum<*>).name.equals(raw, ignoreCase = true) }
                        ?: throw ConfigEditException("«$raw» не подходит: нужно одно из ${choicesOf(entry).joinToString()}")
                } else {
                    choicesOf(entry).firstOrNull { it.equals(raw, ignoreCase = true) }
                        ?: throw ConfigEditException("«$raw» не подходит: нужно одно из ${choicesOf(entry).joinToString()}")
                }
            }
        } catch (error: ConfigEditException) {
            throw error
        } catch (error: RuntimeException) {
            throw ConfigEditException("«$raw» не подходит для настройки ${entry.path}: ${hint(entry.kind)}", error)
        }
    }

    private fun hint(kind: ConfigSettingKind) = when (kind) {
        ConfigSettingKind.INTEGER -> "нужно целое число"
        ConfigSettingKind.DECIMAL -> "нужно число"
        else -> "неверное значение"
    }

    private fun collect(type: Class<*>, prefix: String, chain: List<Field>, depth: Int, out: MutableList<Entry>) {
        if (depth >= MAX_DEPTH) return
        introspector.fields(type).forEach { field ->
            if (field.isAnnotationPresent(ConfigSecret::class.java)) return@forEach
            val path = prefix + introspector.key(field)
            val annotation = field.getAnnotation(ConfigEditable::class.java)
            val kind = kindOf(field)
            if (annotation != null && kind != null && !Modifier.isFinal(field.modifiers)) {
                out += Entry(path, chain + field, annotation, kind)
            } else if (introspector.isSection(field.type)) {
                collect(field.type, "$path.", chain + field, depth + 1, out)
            }
        }
    }

    private fun kindOf(field: Field): ConfigSettingKind? {
        val hint = field.getAnnotation(ConfigHint::class.java)
        if (field.type == String::class.java && hint != null && hint.strict && hint.values.isNotEmpty()) return ConfigSettingKind.CHOICE
        return kindOf(field.type)
    }

    private fun kindOf(type: Class<*>): ConfigSettingKind? = when {
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> ConfigSettingKind.BOOLEAN
        type in WHOLE -> ConfigSettingKind.INTEGER
        type in DECIMAL -> ConfigSettingKind.DECIMAL
        type == String::class.java -> ConfigSettingKind.TEXT
        type.isEnum -> ConfigSettingKind.CHOICE
        else -> null
    }

    private companion object {
        const val MAX_DEPTH = 8
        val WHOLE = setOf<Class<*>>(
            Int::class.javaPrimitiveType!!, Int::class.javaObjectType, Long::class.javaPrimitiveType!!, Long::class.javaObjectType,
            Short::class.javaPrimitiveType!!, Short::class.javaObjectType, Byte::class.javaPrimitiveType!!, Byte::class.javaObjectType,
        )
        val DECIMAL = setOf<Class<*>>(
            Double::class.javaPrimitiveType!!, Double::class.javaObjectType, Float::class.javaPrimitiveType!!, Float::class.javaObjectType,
        )
    }
}
