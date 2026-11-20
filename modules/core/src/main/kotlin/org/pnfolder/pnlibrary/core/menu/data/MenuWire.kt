package org.pnfolder.pnlibrary.core.menu.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.lang.reflect.Type
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** How the menu's questions and answers are written as JSON on the link. */
object MenuWire {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(Instant::class.java, JsonDeserializer { json, _, _ -> Instant.parse(json.asString) })
        .registerTypeAdapter(Duration::class.java, JsonSerializer<Duration> { value, _, _ -> JsonPrimitive(value.toMillis()) })
        .registerTypeAdapter(Duration::class.java, JsonDeserializer { json, _, _ -> Duration.ofMillis(json.asLong) })
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) })
        .create()

    private val chat = GsonComponentSerializer.gson()

    fun encodeLines(lines: List<Component>): List<String> = lines.map(chat::serialize)

    /** The chat messages in [texts]; one that cannot be read is skipped. */
    fun decodeLines(texts: List<String>): List<Component> = texts.mapNotNull { runCatching { chat.deserialize(it) }.getOrNull() }

    inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type
}
