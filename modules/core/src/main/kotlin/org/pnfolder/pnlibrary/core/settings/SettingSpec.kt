package org.pnfolder.pnlibrary.core.settings

/** What kind of value a setting holds; it decides how the value is typed, checked and shown. */
internal enum class SettingKind(val label: String) {
    SWITCH("вкл/выкл"),
    NUMBER("число"),
    TEXT("текст"),
    TIME("время"),
}

/** When a changed value starts to work. */
internal enum class SettingApply(val label: String) {
    LIVE("сразу"),
    RESTART("после перезапуска"),
}

/** A value that cannot be accepted for a setting; [message] says why, in words for the administrator. */
internal class SettingException(message: String) : IllegalArgumentException(message)

/**
 * One setting: its key, kind, limits, description and how to read and change it in [LibrarySettings].
 *
 * @property key the name used in commands and in `settings.json`, such as `downloads.enabled`
 * @property title the short name shown in the menu
 * @property field the property of [LibrarySettings] it controls
 * @property min lowest number, or lowest length in milliseconds for a time
 * @property max highest number, or highest length in milliseconds for a time
 */
internal class SettingSpec(
    val key: String,
    val title: String,
    val field: String,
    val kind: SettingKind,
    val description: String,
    val applies: SettingApply,
    val min: Long? = null,
    val max: Long? = null,
    val examples: List<String> = emptyList(),
    private val read: (LibrarySettings) -> Any,
    private val write: (LibrarySettings, Any) -> LibrarySettings,
    private val parse: (String) -> Any,
    private val show: (Any) -> String,
) {
    /** The value of this setting in [settings], as the text an administrator would type. */
    fun valueIn(settings: LibrarySettings): String = show(read(settings))

    /** The default value as text. */
    val default: String get() = valueIn(LibrarySettings())

    /** [settings] with this setting changed to [text].
     * @throws SettingException when [text] is not acceptable */
    fun apply(settings: LibrarySettings, text: String): LibrarySettings = write(settings, parse(text))

    /** [text] as it is shown after being accepted (`1h30m` and `90m` are the same time).
     * @throws SettingException when [text] is not acceptable */
    fun normalize(text: String): String = show(parse(text))

    /** The range in words, or an empty text when the value has no range. */
    fun range(): String = when {
        kind == SettingKind.NUMBER && min != null && max != null -> "от $min до $max"
        kind == SettingKind.TIME && min != null && max != null -> "от ${SettingTexts.duration(min)} до ${SettingTexts.duration(max)}"
        kind == SettingKind.TEXT && max != null -> "до $max символов"
        else -> ""
    }
}
