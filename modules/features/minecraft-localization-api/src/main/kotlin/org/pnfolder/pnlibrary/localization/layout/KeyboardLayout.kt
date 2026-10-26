package org.pnfolder.pnlibrary.localization.layout

private const val KEY_COUNT = 35

/**
 * A keyboard layout: the characters on a fixed list of 35 physical keys, which are the letter block, the punctuation
 * keys beside it that carry letters in other layouts, backslash, backtick and minus. Digits and shifted symbols are
 * out of scope. Conversions follow key positions, so a layout is just one more `keys` string. The built-in strings
 * were read from the xkeyboard-config symbol files of each layout.
 *
 * Latin QWERTY languages that add no letters on these keys (English-GB, Polish, Dutch, ...) need no entry of their own:
 * they read the same as [EN_US]. Scripts that compose characters from several keys or need ligatures (Korean,
 * Japanese, Arabic's lam-alef) cannot be described as one character per key and are out of scope.
 *
 * @property id short layout identifier, such as `en_us`
 */
class KeyboardLayout private constructor(val id: String, private val keys: String) {
    /** Two layouts are equal when both their id and key characters match. */
    override fun equals(other: Any?): Boolean = other is KeyboardLayout && id == other.id && keys == other.keys

    /** Hash consistent with [equals]. */
    override fun hashCode(): Int = 31 * id.hashCode() + keys.hashCode()

    /** The layout [id]. */
    override fun toString(): String = id

    /** The built-in layouts. */
    companion object {
        /** US QWERTY, the reference for the key order. */
        @JvmField
        val EN_US = of("en_us", "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-")

        /** Russian ЙЦУКЕН. */
        @JvmField
        val RU = of("ru", "йцукенгшщзхъ\\фывапролджэячсмитьбюё-")

        /** Ukrainian ЙЦУКЕН. */
        @JvmField
        val UK = of("uk", "йцукенгшщзхїґфівапролджєячсмитьбю'-")

        /** German QWERTZ. */
        @JvmField
        val DE = of("de", "qwertzuiopü+#asdfghjklöäyxcvbnm,.^ß")

        /** Belarusian ЙЦУКЕН. */
        @JvmField
        val BE = of("be", "йцукенгшўзх'\\фывапролджэячсмітьбюё-")

        /** French AZERTY. */
        @JvmField
        val FR = of("fr", "azertyuiop^\$*qsdfghjklmùwxcvbn,;:²)")

        /** Spanish (Spain). */
        @JvmField
        val ES = of("es", "qwertyuiop`+çasdfghjklñ´zxcvbnm,.º'")

        /** Italian. */
        @JvmField
        val IT = of("it", "qwertyuiopè+ùasdfghjklòàzxcvbnm,.\\'")

        /** Brazilian Portuguese ABNT2. */
        @JvmField
        val PT_BR = of("pt_br", "qwertyuiop´[]asdfghjklç~zxcvbnm,.'-")

        /** Turkish Q. */
        @JvmField
        val TR_Q = of("tr_q", "qwertyuıopğü,asdfghjklşizxcvbnmöç\"*")

        /**
         * Czech QWERTZ; its accented letters on the number row are out of scope. The section sign on the quote key is
         * spelled by code (167) because SourceConventionsTest forbids the literal character in sources.
         */
        @JvmField
        val CS = of("cs", "qwertzuiopú)¨asdfghjklů${Char(167)}yxcvbnm,.;=")

        /** Greek. */
        @JvmField
        val GREEK = of("el", ";ςερτυθιοπ[]\\ασδφγηξκλ´'ζχψωβνμ,.`-")

        /** Hebrew (SI-1452). */
        @JvmField
        val HE = of("he", "/'קראטוןםפ][\\שדגכעיחלךף,זסבהנמצתץ;-")

        /** The layouts the library knows, in a fixed order. */
        @JvmField
        val builtIn: List<KeyboardLayout> = listOf(EN_US, RU, UK, BE, DE, FR, ES, IT, PT_BR, TR_Q, CS, GREEK, HE)

        /**
         * Creates a layout from [keys], the characters on the physical keys in US QWERTY order: `q w e r t y u i o p [ ]
         * \`, then `a s d f g h j k l ; '`, then `z x c v b n m , .`, then `` ` `` and `-`. That is exactly 35
         * distinct characters, lower case; [id] must not be blank.
         *
         * To extend the built-in set for a call:
         * ```kotlin
         * val mine = KeyboardLayout.builtIn + KeyboardLayout.of("xx", "...")
         * index.suggest("...", 5, mine)
         * ```
         */
        @JvmStatic
        fun of(id: String, keys: String): KeyboardLayout {
            require(id.isNotBlank()) { "Keyboard layout id must not be blank" }
            require(keys.length == KEY_COUNT) { "Keyboard layout '$id' needs exactly $KEY_COUNT keys, not ${keys.length}" }
            val repeated = keys.groupBy { it }.filterValues { it.size > 1 }.keys
            require(repeated.isEmpty()) { "Keyboard layout '$id' repeats the key characters $repeated" }
            return KeyboardLayout(id, keys)
        }

        /**
         * Reads [text], typed while [from] was active, as the keys would read under [to]. Case is kept and characters
         * that are not keys of [from] pass through unchanged.
         */
        @JvmStatic
        fun convert(text: String, from: KeyboardLayout, to: KeyboardLayout): String = text.map { char ->
            val position = from.keys.indexOf(char.lowercaseChar())
            if (position < 0) char else to.keys[position].let { if (char.isUpperCase()) it.uppercaseChar() else it }
        }.joinToString("")

        /** The [layouts] that contain every letter of [text]; empty when the text has no letters or mixes layouts. */
        @JvmStatic
        @JvmOverloads
        fun detect(text: String, layouts: Collection<KeyboardLayout> = builtIn): List<KeyboardLayout> {
            val letters = text.filter { it.isLetter() }.lowercase()
            return if (letters.isEmpty()) emptyList() else layouts.filter { layout -> letters.all { it in layout.keys } }
        }

        /** Every distinct re-reading of [text] from each detected layout into each other of [layouts], never [text] itself. */
        @JvmStatic
        @JvmOverloads
        fun alternatives(text: String, layouts: Collection<KeyboardLayout> = builtIn): List<String> =
            detect(text, layouts).flatMap { from -> layouts.filter { it != from }.map { to -> convert(text, from, to) } }
                .filter { it != text }.distinct()
    }
}
