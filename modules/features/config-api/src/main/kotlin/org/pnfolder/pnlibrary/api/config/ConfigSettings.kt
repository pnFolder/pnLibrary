package org.pnfolder.pnlibrary.api.config

/** When an edited setting starts to work. */
enum class ConfigApply {
    /** At once: the plugin reads the value every time it needs it. */
    LIVE,

    /** After the file is read again: the plugin takes the value when it handles [ManagedConfig.onReloadRequired]. */
    RELOAD,

    /** After the server restarts. */
    RESTART,
}

/**
 * Shows a field in the in-game settings menu (`/pn gui`) and lets `/pn configs edit` change it.
 *
 * Only marked fields are shown; secrets ([ConfigSecret]) never are. Supported types: boolean, whole and decimal
 * numbers, text and enums. A [ConfigRange] on the field limits a number, [ConfigStep] sets the click step,
 * [ConfigDuration] marks a length of time and [ConfigHint] describes a text.
 *
 * ```kotlin
 * @field:ConfigEditable("Приветствие", "Текст, который видит игрок при входе.")
 * var greeting: String = "Добро пожаловать!"
 *
 * @field:ConfigEditable("Размер пула", "Сколько соединений держать открытыми.", apply = ConfigApply.RESTART)
 * @field:ConfigRange(min = 1.0, max = 64.0)
 * var poolSize: Int = 10
 * ```
 *
 * @property title short name for a player; the YAML key when empty
 * @property description what the setting does, shown next to it
 * @property apply when a change starts to work
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigEditable(val title: String = "", val description: String = "", val apply: ConfigApply = ConfigApply.LIVE)

/**
 * How far one click changes a number in the settings menu, and the unit shown next to it.
 *
 * The menu also offers ten, a hundred and a thousand steps and lets the viewer type the number.
 *
 * @property step the smallest change of one click
 * @property unit what the number counts, such as `блоков` or `%`
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigStep(val step: Double = 1.0, val unit: String = "")

/**
 * Marks a whole number as a length of time kept in [unit]. The menu shows it as `1 ч 30 мин`, steps by seconds,
 * minutes or hours, and accepts typed values such as `90m`, `1h30m` or `45с`.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigDuration(val unit: java.util.concurrent.TimeUnit)

/**
 * Tells the person who edits a text what it may hold.
 *
 * @property format how the text is written, such as `мир;x;y;z`
 * @property examples sample values shown in the menu
 * @property values the known values; with [strict] only they are accepted and the menu offers them as a list
 * @property strict whether anything outside [values] is refused
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigHint(
    val format: String = "",
    val examples: Array<String> = [],
    val values: Array<String> = [],
    val strict: Boolean = false,
)

/** What kind of value a setting holds, which decides the control that edits it. */
enum class ConfigSettingKind { BOOLEAN, INTEGER, DECIMAL, TEXT, CHOICE }

/**
 * One editable setting of a configuration file.
 *
 * @property path dotted YAML path, such as `shop.greeting`
 * @property title short name for a player
 * @property description what it does
 * @property kind the kind of value
 * @property value current value as text, or `null` while the file is not loaded
 * @property choices the allowed values of a [ConfigSettingKind.CHOICE], empty otherwise
 * @property min lower bound of a number, or `null`
 * @property max upper bound of a number, or `null`
 * @property apply when a change starts to work
 * @property unit what a number counts, empty when it counts nothing in particular
 * @property step the smallest change of one click on a number
 * @property duration the unit of a number that holds a length of time, or `null`
 * @property format how a text is written, empty when free
 * @property examples sample values of a text
 */
data class ConfigSetting(
    val path: String,
    val title: String,
    val description: String,
    val kind: ConfigSettingKind,
    val value: String?,
    val choices: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val apply: ConfigApply = ConfigApply.LIVE,
    val unit: String = "",
    val step: Double = 1.0,
    val duration: java.util.concurrent.TimeUnit? = null,
    val format: String = "",
    val examples: List<String> = emptyList(),
)

/** The result of changing one setting: [setting] holds the new value, [previous] the old one. */
data class ConfigEditResult(val setting: ConfigSetting, val previous: String?)

/** A setting could not be changed; the message says why and is meant to be shown to the person who typed the value. */
class ConfigEditException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** What happened to a file. */
enum class ConfigChangeCause { LOADED, RELOADED, SAVED, EDITED, RESET }

/**
 * A file changed: it was read, written, or one of its settings was edited.
 *
 * @property id the file
 * @property cause what happened
 * @property paths YAML paths that changed, empty when the whole file is meant
 */
data class ConfigChange(val id: ConfigId?, val cause: ConfigChangeCause, val paths: List<String>)

/**
 * A setting that does not work yet was changed: the plugin has to take it over ([ConfigApply.RELOAD]) or the server has
 * to restart ([ConfigApply.RESTART]).
 *
 * @property id the file
 * @property apply the strongest requirement among the [settings]
 * @property settings the settings that were changed, with their new values
 */
data class ReloadRequest(val id: ConfigId?, val apply: ConfigApply, val settings: List<ConfigSetting>)
