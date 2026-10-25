package org.pnfolder.pnlibrary.api.config

/**
 * Reads a polymorphic value from one line of text instead of a YAML object.
 *
 * Declare it with [ConfigTextForm] on the polymorphic base type. The line `"[message] Hello"` can then
 * stand where the full object `{ type: message, messages: [Hello] }` is accepted. Objects keep working.
 *
 * Implementations should reject invalid text with a descriptive exception; pnLibrary adds the path.
 */
interface ConfigTextParser<T : Any> {
    /** Converts [text] into a value of the polymorphic base type. */
    fun parse(text: String, context: ConfigSerializationContext): T
}
