package ru.privatenull.pnlibrary.api.config

/** Lines written above a YAML field. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigComment(vararg val value: String)

/** Overrides the YAML key without renaming the Java or Kotlin field. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigKey(val value: String)

/** Excludes a field from loading and saving. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigIgnore

/** Inserts an empty line before a field. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNewLine

/** Controls field order; lower values are written first. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigOrder(val value: Int)

/** Requires a numeric field to stay inside the inclusive range. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigRange(val min: Double = -Double.MAX_VALUE, val max: Double = Double.MAX_VALUE)

/** Rejects null, empty, or whitespace-only text. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNotBlank

/** Requires the complete text value to match [value]. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPattern(val value: String)
