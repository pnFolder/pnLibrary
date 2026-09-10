package ru.privatenull.pnlibrary.api.config

/** Converts a custom type to/from YAML-compatible scalar, list, or map values. */
interface ConfigSerializer<T : Any> {
    fun serialize(value: T): Any?
    fun deserialize(value: Any?): T
}
