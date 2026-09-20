package ru.privatenull.pnlibrary.localization.internal

import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.localization.TranslationException
import java.nio.charset.StandardCharsets

internal object TranslationJsonCodec {
    fun parse(bytes: ByteArray): Map<String, String> = try {
        val text = String(bytes, StandardCharsets.UTF_8)
        val values = if (text.trimStart().startsWith("{")) {
            JsonParser.parseString(text).asJsonObject.entrySet()
                .associateTo(linkedMapOf()) { (key, value) -> key to value.asString }
        } else {
            text.lineSequence().map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') && '=' in it }
                .associateTo(linkedMapOf()) { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
        }
        if (values.isEmpty()) throw IllegalArgumentException("Minecraft language table is empty")
        values
    } catch (error: Exception) {
        throw TranslationException(TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft language file", error)
    }
}
