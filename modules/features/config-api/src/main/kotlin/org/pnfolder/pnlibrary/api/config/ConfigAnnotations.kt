package org.pnfolder.pnlibrary.api.config

import kotlin.reflect.KClass

/**
 * Writes documentation lines above a YAML field or at the beginning of a configuration class.
 *
 * ```kotlin
 * @field:ConfigComment("Maximum connections retained by the pool.", "Range: 1..64")
 * var poolSize: Int = 10
 * ```
 * produces `# Maximum connections retained by the pool.` followed by `pool-size: 10` when kebab
 * naming is enabled. Each argument becomes one comment line without the `#` prefix.
 *
 * @property value documentation lines written without YAML comment prefixes
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigComment(vararg val value: String)

/**
 * Overrides the YAML key without renaming the Java or Kotlin field.
 *
 * For example, `@field:ConfigKey("api-token") var token = ""` reads and writes `api-token`.
 * This explicit name takes precedence over [ConfigOptions.naming].
 *
 * @property value exact YAML key used for reading and writing the field
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigKey(val value: String)

/**
 * Excludes a field from loading, validation, and saving.
 *
 * In Kotlin use `@field:ConfigIgnore` so the annotation is placed on the backing field inspected
 * by the codec. Static, transient, and synthetic fields are ignored independently.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigIgnore

/**
 * Marks a field whose value must never appear in a diagnostic report, such as a password, a token or a webhook.
 *
 * It applies to the file this class is registered for, and to a nested section when put on the field holding it.
 * The value is hidden in every report with no entry in `plugins/pnLibrary/config.yml`:
 * ```kotlin
 * @field:ConfigSecret
 * var password: String = ""
 * ```
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigSecret

/** Inserts an empty line before the annotated field when pnLibrary renders YAML. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNewLine

/**
 * Controls generated field order; lower values are written first.
 *
 * Fields with equal order retain reflection discovery order, so assign explicit distinct values
 * when a stable public file layout matters.
 *
 * @property value ascending sort position used while rendering fields
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigOrder(val value: Int)

/**
 * Requires a numeric field to remain inside the inclusive range [[min], [max]].
 *
 * The annotation is validated after decoding. Applying it to a non-numeric value produces a
 * configuration problem rather than silently accepting the field.
 *
 * @property min inclusive minimum accepted numeric value
 * @property max inclusive maximum accepted numeric value
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigRange(val min: Double = -Double.MAX_VALUE, val max: Double = Double.MAX_VALUE)

/** Rejects a value unless it is a non-null [String] containing at least one non-whitespace character. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigNotBlank

/**
 * Requires the complete text value to match the Java regular expression in [value].
 *
 * For example, `@field:ConfigPattern("[a-z][a-z0-9_-]{2,31}")` validates an entire identifier,
 * not merely a substring. Invalid regex syntax is reported while the model is inspected.
 *
 * @property value Java regular expression matched against the complete string
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPattern(val value: String)

/**
 * Uses a [ConfigSerializer] implementation for this field or every occurrence of this class.
 *
 * The serializer class must expose a no-argument constructor or Kotlin `object` instance. A field
 * annotation takes precedence over a class annotation and a scope-registered serializer.
 *
 * @property value serializer implementation instantiated for the annotated value
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigSerializeWith(val value: KClass<out ConfigSerializer<*>>)

/**
 * Fails loading when this key is physically absent from an existing YAML file.
 *
 * Unlike [MissingValuePolicy.ADD], this is an explicit schema requirement and is checked before
 * missing defaults are merged. A present key whose value is invalid fails through normal decoding
 * or validation.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigRequired

/**
 * Uses the code-defined field value when YAML contains an invalid value and emits a warning.
 *
 * Recovery is local to the annotated field. Other invalid fields still fail loading, and the
 * fallback does not hide semantic validation errors reported after object construction.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigDefaultOnInvalid

/**
 * Declares previous YAML spellings accepted for an enum constant.
 *
 * ```kotlin
 * enum class StorageMode {
 *     @ConfigAlias("MYSQL", "MARIADB")
 *     JDBC
 * }
 * ```
 * Both legacy values deserialize as `JDBC`; serialization always writes `JDBC`.
 *
 * @property value legacy enum spellings accepted during deserialization
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigAlias(vararg val value: String)

/**
 * Marks an interface or abstract class as a polymorphic configuration value.
 *
 * [discriminator] names the map key selecting an implementation. With the default discriminator,
 * a value can look like `{ type: mysql, host: localhost }`. Accepted implementations come from
 * [ConfigTypes] and visible dynamic registrations in [ConfigScope.type].
 *
 * @property discriminator map key containing the implementation identifier
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigPolymorphic(val discriminator: String = "type")

/**
 * Declares implementations accepted by a [ConfigPolymorphic] type.
 *
 * Place it on the base type for global declarations or on a field to extend choices only at that
 * location. Every [ConfigType.type] must implement the annotated base type.
 *
 * @property value implementation declarations available at the annotation site
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigTypes(vararg val value: ConfigType)

/**
 * Associates one polymorphic implementation with its discriminator identifier.
 *
 * @property type concrete class instantiated for this choice
 * @property name primary case-insensitive discriminator value written during serialization
 * @property aliases additional case-insensitive values accepted only during deserialization
 * @property priority tie-break priority when multiple declarations accept the same identifier
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigType(
    val type: KClass<*>,
    val name: String,
    val aliases: Array<String> = [],
    val priority: Int = 0,
)

/** A kind of database pnLibrary can copy before an update and put back on a rollback. */
enum class DatabaseKind {
    /** MySQL and MariaDB. */
    MYSQL,

    /** MongoDB; documents are copied with their indexes, without a snapshot in time. */
    MONGODB,

    /** PostgreSQL; tables of the current schema are copied with indexes, constraints and sequences. */
    POSTGRESQL,
}

/**
 * Declares a database that pnLibrary copies before an update of this plugin and can restore when the update is
 * rolled back. Repeatable, so one class can describe several databases; their names must differ within a plugin.
 *
 * Three placements are supported:
 * - On a field holding a connection section (a nested class): that section's fields describe the connection.
 * - On a scalar `String` field: the field is the database name, and the other connection fields of the same class
 *   are tied to it by [DatabaseField] with the same [name].
 * - On the configuration class itself, when the connection fields sit at the top of the file.
 *
 * Connection fields are found by their usual names (`host`, `port`, `database`, `user` or `username`, `password`);
 * mark them with [DatabaseField] to use any name. Mark the password with [ConfigSecret] so reports hide it.
 * ```kotlin
 * @field:BackupDatabase(DatabaseKind.POSTGRESQL, "Clans", tables = ["clans", "clan_members"])
 * var clansDb = "clans"
 * @field:DatabaseField(DatabaseRole.HOST, database = "Clans")
 * var clansHost = "pg.local"
 * ```
 * Only tables (collections in MongoDB) are copied; triggers, procedures and views are not.
 *
 * @property kind the database server type
 * @property name short name shown by the menu and commands; also the key that ties [DatabaseField] and [BackupTable] to it
 * @property tables tables or collections to copy; `*` in a name matches any text (`"log_*"`); empty means all of them
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class BackupDatabase(val kind: DatabaseKind, val name: String, val tables: Array<String> = [])

/** What a field of a database connection section holds. */
enum class DatabaseRole {
    /** Server host name or address. */
    HOST,

    /** Server port. */
    PORT,

    /** Database (schema) name. */
    DATABASE,

    /** Login of the account. */
    USER,

    /** Password of the account; also mark the field with [ConfigSecret]. */
    PASSWORD,

    /** A full connection string such as `mongodb://user:pass@host/db`; when present it replaces host, port, user and password. */
    URI,
}

/**
 * Says which part of a connection a field holds, so the field can have any name. Use it next to [BackupDatabase];
 * fields without it are found by their usual names.
 *
 * With several databases in one class, [database] names the [BackupDatabase.name] the field belongs to. A field
 * without [database] is shared by all of them, and a field with it wins over a shared one.
 * ```kotlin
 * @field:DatabaseField(DatabaseRole.HOST)
 * var address = "localhost"
 * ```
 *
 * @property role which part of the connection the field holds
 * @property database [BackupDatabase.name] this field belongs to; empty means every database of the class
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class DatabaseField(val role: DatabaseRole, val database: String = "")

/**
 * Marks a text field that holds the name of a table (or collection) to copy, for plugins whose table names are set
 * in the configuration. Several fields may carry it; together with [BackupDatabase.tables] both lists are joined.
 * ```kotlin
 * @field:BackupTable
 * var table = "clans"
 * ```
 *
 * @property database [BackupDatabase.name] this table belongs to; empty means every database of the class
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class BackupTable(val database: String = "")
