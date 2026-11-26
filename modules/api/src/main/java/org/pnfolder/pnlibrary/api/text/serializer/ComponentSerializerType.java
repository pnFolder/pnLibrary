package org.pnfolder.pnlibrary.api.text.serializer;

/**
 * Text syntax used to convert between strings and Adventure components.
 *
 * <p>Pass a value to {@code ComponentService.deserialize} when the source format is known:
 *
 * <pre>{@code
 * Component title = components.deserialize(
 *     "<gold><bold>Server started</bold></gold>",
 *     ComponentSerializerType.MINI_MESSAGE);
 * }</pre>
 */
public enum ComponentSerializerType {
    /**
     * Accepts JSON, MiniMessage, and common legacy formatting in one input format.
     *
     * <p>A value beginning with <code>&#123;</code> or {@code [} is parsed as Adventure JSON when it is valid.
     * Every other value is parsed as MiniMessage after converting legacy {@code &} and {@code §}
     * codes, six-digit hex colors, and the old gradient form.
     *
     * <pre>
     * &amp;aSuccess                       -&gt; green "Success"
     * &amp;#55ff55Success                 -&gt; #55ff55 "Success"
     * &lt;gradient:red:gold&gt;Hi&lt;/gradient&gt; -&gt; red-to-gold MiniMessage gradient
     * {"text":"Hi","color":"gold"} -&gt; gold Adventure JSON component
     * </pre>
     */
    ADAPTIVE,

    /**
     * Adventure MiniMessage markup.
     *
     * <pre>
     * &lt;yellow&gt;Hello &lt;bold&gt;Alex&lt;/bold&gt;&lt;/yellow&gt;
     * &lt;gradient:#ff0000:#ffff00&gt;Warning&lt;/gradient&gt;
     * &lt;click:open_url:'https://example.com'&gt;Open website&lt;/click&gt;
     * </pre>
     *
     * <p>Legacy {@code &a} codes are ordinary text in this mode.
     */
    MINI_MESSAGE,

    /**
     * Legacy Minecraft formatting codes introduced by {@code &}.
     *
     * <pre>
     * &amp;aGreen text &amp;lwith bold text&amp;r and normal text
     * </pre>
     *
     * <p>MiniMessage tags such as {@code <green>} are ordinary text in this mode.
     */
    LEGACY_AMPERSAND,

    /**
     * Legacy Minecraft formatting codes introduced by the section sign ({@code §}).
     *
     * <pre>
     * §cRed text §lwith bold text§r and normal text
     * </pre>
     *
     * <p>This format is mainly useful for native server strings that already contain
     * section-sign codes. Configuration files should usually use {@link #MINI_MESSAGE} or
     * {@link #LEGACY_AMPERSAND}.
     */
    LEGACY_SECTION,

    /**
     * Adventure's JSON component representation.
     *
     * <pre>
     * {"text":"Welcome ","color":"yellow","extra":[{"text":"Alex","bold":true}]}
     * </pre>
     *
     * <p>Invalid JSON or an invalid component structure causes deserialization to fail.
     */
    ADVENTURE_JSON,

    /**
     * Literal text with no formatting interpretation.
     *
     * <pre>
     * &lt;red&gt;This stays literal&lt;/red&gt; and so does &amp;a this code
     * </pre>
     *
     * <p>Use this for user-generated content that must never activate markup.
     */
    PLAIN_TEXT
}
