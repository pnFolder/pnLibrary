package org.pnfolder.pnlibrary.api.config;

/** Converts Java/Kotlin field names into YAML keys. {@link ConfigKey} always wins. */
public enum ConfigNamingStrategy {
    /** Preserve the field name exactly as declared in source. */
    AS_DECLARED,

    /** Write {@code connectionTimeout} style keys. */
    CAMEL_CASE,

    /** Write {@code connection_timeout} style keys. */
    SNAKE_CASE,

    /** Write {@code connection-timeout} style keys. */
    KEBAB_CASE,

    /** Write {@code CONNECTION_TIMEOUT} style keys. */
    UPPER_SNAKE_CASE
}
