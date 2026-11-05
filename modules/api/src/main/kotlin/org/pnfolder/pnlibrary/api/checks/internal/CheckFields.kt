package org.pnfolder.pnlibrary.api.checks.internal

import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.internal.InternalPnLibraryApi
import java.util.Locale

/** Reads the keys of one object ignoring case, `_`, `-` and spaces in their spelling. */
@OptIn(InternalPnLibraryApi::class)
internal class Fields(source: Map<String, Any?>) {
    private val byKey = source.entries.associate { normalize(it.key) to it.value }

    fun raw(vararg keys: String): Any? = keys.firstNotNullOfOrNull { byKey[normalize(it)] }

    fun text(vararg keys: String): String? = raw(*keys)?.toString()

    fun required(vararg keys: String): String =
        text(*keys) ?: throw IllegalArgumentException("Missing '${keys.first()}'")

    fun input(): String = required("input")

    fun output(): String = text("output") ?: ""

    fun int(key: String): Int? = raw(key)?.let {
        (it as? Number)?.toInt() ?: it.toString().trim().toIntOrNull()
            ?: throw IllegalArgumentException("'$key' must be a whole number but was '$it'")
    }

    fun double(key: String): Double? = raw(key)?.let {
        (it as? Number)?.toDouble() ?: it.toString().trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("'$key' must be a number but was '$it'")
    }

    fun bool(key: String): Boolean? = raw(key)?.let {
        it as? Boolean ?: it.toString().trim().lowercase(Locale.ROOT).let { text ->
            when (text) {
                "true", "yes", "on" -> true
                "false", "no", "off" -> false
                else -> throw IllegalArgumentException("'$key' must be true or false but was '$it'")
            }
        }
    }

    fun list(key: String): List<String> = when (val value = raw(key)) {
        null -> emptyList()
        is Iterable<*> -> value.map { it.toString() }
        else -> listOf(value.toString())
    }

    fun condition(key: String): Check? = raw(key)?.let { CheckSerializer.read(it, key) }

    fun checks(key: String): List<Check> = when (val value = raw(key)) {
        null -> emptyList()
        is Iterable<*> -> value.map { CheckSerializer.read(it, key) }
        else -> throw IllegalArgumentException("'$key' must be a list of checks")
    }

    /** A probability as `0.25` or `25%`. */
    fun probability(key: String): Double? = raw(key)?.let {
        val text = it.toString().trim()
        val number = if (text.endsWith("%")) text.dropLast(1).trim().toDoubleOrNull()?.div(100) else text.toDoubleOrNull()
        number ?: throw IllegalArgumentException("'$key' must be a probability such as 0.25 or 25% but was '$it'")
    }

    companion object {
        fun normalize(key: String): String =
            key.lowercase(Locale.ROOT).filter { it != '_' && it != '-' && it != ' ' }
    }
}
