package org.pnfolder.pnlibrary.api.config

/** Converts Java/Kotlin field names into YAML keys. [ConfigKey] always wins. */
enum class ConfigNamingStrategy {
    /** Preserve the field name exactly as declared in source. */
    AS_DECLARED,

    /** Write `connectionTimeout` style keys. */
    CAMEL_CASE,

    /** Write `connection_timeout` style keys. */
    SNAKE_CASE,

    /** Write `connection-timeout` style keys. */
    KEBAB_CASE,

    /** Write `CONNECTION_TIMEOUT` style keys. */
    UPPER_SNAKE_CASE,
}
