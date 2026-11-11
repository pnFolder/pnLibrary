package org.pnfolder.pnlibrary.config.yaml

import org.pnfolder.pnlibrary.api.config.ConfigAlias

private const val MAX_SHOWN = 120

/** A value for an error message, cut to a readable length. */
internal fun shown(raw: Any?): String {
    val text = raw?.toString() ?: "null"
    return if (text.length > MAX_SHOWN) text.take(MAX_SHOWN) + "..." else text
}

internal fun reasonSuffix(error: Exception): String =
    if (error is IllegalArgumentException && !error.message.isNullOrBlank()) " Reason: ${error.message}" else ""

internal fun enumAlias(type: Class<*>, value: Any?, path: String): Any? {
    if (!type.isEnum || value !is String) return value
    val matches = type.enumConstants.map { it as Enum<*> }.filter { constant ->
        type.getField(constant.name).getAnnotation(ConfigAlias::class.java)
            ?.value.orEmpty().any { it.equals(value, ignoreCase = true) }
    }
    require(matches.size <= 1) { "Enum alias '$value' is ambiguous at $path in ${type.name}" }
    return matches.singleOrNull()?.name ?: value
}
