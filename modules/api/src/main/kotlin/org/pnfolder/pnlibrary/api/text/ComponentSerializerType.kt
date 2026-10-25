package org.pnfolder.pnlibrary.api.text

/**
 * Text syntax used to convert between strings and Adventure components.
 *
 * Pass a value to [ComponentService.deserialize] when the source format is known:
 *
 * ```kotlin
 * val title = components.deserialize(
 *     "<gold><bold>Server started</bold></gold>",
 *     ComponentSerializerType.MINI_MESSAGE,
 * )
 * ```
 */
enum class ComponentSerializerType {
    /**
     * Accepts JSON, MiniMessage, and common legacy formatting in one input format.
     *
     * A value beginning with `{` or `[` is parsed as Adventure JSON when it is valid.
     * Every other value is parsed as MiniMessage after converting legacy `&` and `§`
     * codes, six-digit hex colors, and the old gradient form.
     *
     * Examples:
     *
     * ```text
     * &aSuccess                       -> green "Success"
     * &#55ff55Success                 -> #55ff55 "Success"
     * <gradient:red:gold>Hi</gradient> -> red-to-gold MiniMessage gradient
     * {"text":"Hi","color":"gold"} -> gold Adventure JSON component
     * ```
     */
    ADAPTIVE,
    /**
     * Adventure MiniMessage markup.
     *
     * ```text
     * <yellow>Hello <bold>Alex</bold></yellow>
     * <gradient:#ff0000:#ffff00>Warning</gradient>
     * <click:open_url:'https://example.com'>Open website</click>
     * ```
     *
     * Legacy `&a` codes are ordinary text in this mode.
     */
    MINI_MESSAGE,
    /**
     * Legacy Minecraft formatting codes introduced by `&`.
     *
     * ```text
     * &aGreen text &lwith bold text&r and normal text
     * ```
     *
     * MiniMessage tags such as `<green>` are ordinary text in this mode.
     */
    LEGACY_AMPERSAND,
    /**
     * Legacy Minecraft formatting codes introduced by the section sign (`§`).
     *
     * ```text
     * §cRed text §lwith bold text§r and normal text
     * ```
     *
     * This format is mainly useful for native server strings that already contain
     * section-sign codes. Configuration files should usually use [MINI_MESSAGE] or
     * [LEGACY_AMPERSAND].
     */
    LEGACY_SECTION,
    /**
     * Adventure's JSON component representation.
     *
     * ```json
     * {"text":"Welcome ","color":"yellow","extra":[{"text":"Alex","bold":true}]}
     * ```
     *
     * Invalid JSON or an invalid component structure causes deserialization to fail.
     */
    ADVENTURE_JSON,
    /**
     * Literal text with no formatting interpretation.
     *
     * ```text
     * <red>This stays literal</red> and so does &a this code
     * ```
     *
     * Use this for user-generated content that must never activate markup.
     */
    PLAIN_TEXT,
}
