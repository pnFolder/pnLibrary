package org.pnfolder.pnlibrary.localization.layout;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A keyboard layout: the characters on a fixed list of 35 physical keys, which are the letter block, the punctuation
 * keys beside it that carry letters in other layouts, backslash, backtick and minus. Digits and shifted symbols are
 * out of scope. Conversions follow key positions, so a layout is just one more {@code keys} string. The built-in
 * strings were read from the xkeyboard-config symbol files of each layout.
 *
 * <p>Latin QWERTY languages that add no letters on these keys (English-GB, Polish, Dutch, ...) need no entry of their
 * own: they read the same as {@link #EN_US}. Scripts that compose characters from several keys or need ligatures
 * (Korean, Japanese, Arabic's lam-alef) cannot be described as one character per key and are out of scope.
 *
 * <pre>{@code
 * List<KeyboardLayout> mine = new ArrayList<>(KeyboardLayout.builtIn);
 * mine.add(KeyboardLayout.of("xx", "..."));
 * index.suggest("...", 5, mine);
 * }</pre>
 */
public final class KeyboardLayout {
    private static final int KEY_COUNT = 35;

    /** US QWERTY, the reference for the key order. */
    public static final KeyboardLayout EN_US = of("en_us", "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-");
    /** Russian JCUKEN layout. */
    public static final KeyboardLayout RU = of("ru", "йцукенгшщзхъ\\фывапролджэячсмитьбюё-");
    /** Ukrainian JCUKEN layout. */
    public static final KeyboardLayout UK = of("uk", "йцукенгшщзхїґфівапролджєячсмитьбю'-");
    /** German QWERTZ. */
    public static final KeyboardLayout DE = of("de", "qwertzuiopü+#asdfghjklöäyxcvbnm,.^ß");
    /** Belarusian JCUKEN layout. */
    public static final KeyboardLayout BE = of("be", "йцукенгшўзх'\\фывапролджэячсмітьбюё-");
    /** French AZERTY. */
    public static final KeyboardLayout FR = of("fr", "azertyuiop^$*qsdfghjklmùwxcvbn,;:²)");
    /** Spanish (Spain). */
    public static final KeyboardLayout ES = of("es", "qwertyuiop`+çasdfghjklñ´zxcvbnm,.º'");
    /** Italian. */
    public static final KeyboardLayout IT = of("it", "qwertyuiopè+ùasdfghjklòàzxcvbnm,.\\'");
    /** Brazilian Portuguese ABNT2. */
    public static final KeyboardLayout PT_BR = of("pt_br", "qwertyuiop´[]asdfghjklç~zxcvbnm,.'-");
    /** Turkish Q. */
    public static final KeyboardLayout TR_Q = of("tr_q", "qwertyuıopğü,asdfghjklşizxcvbnmöç\"*");
    /**
     * Czech QWERTZ; its accented letters on the number row are out of scope. The section sign on the quote key is
     * spelled by code (167) because the source conventions forbid the literal character in sources.
     */
    public static final KeyboardLayout CS = of("cs", "qwertzuiopú)¨asdfghjklů" + (char) 167 + "yxcvbnm,.;=");
    /** Greek. */
    public static final KeyboardLayout GREEK = of("el", ";ςερτυθιοπ[]\\ασδφγηξκλ´'ζχψωβνμ,.`-");
    /** Hebrew (SI-1452). */
    public static final KeyboardLayout HE = of("he", "/'קראטוןםפ][\\שדגכעיחלךף,זסבהנמצתץ;-");

    /** The layouts the library knows, in a fixed order. */
    public static final List<KeyboardLayout> builtIn = Collections.unmodifiableList(
            Arrays.asList(EN_US, RU, UK, BE, DE, FR, ES, IT, PT_BR, TR_Q, CS, GREEK, HE));

    private final String id;
    private final String keys;

    private KeyboardLayout(String id, String keys) {
        this.id = id;
        this.keys = keys;
    }

    /** Short layout identifier, such as {@code en_us}. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Two layouts are equal when both their id and key characters match. */
    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardLayout
                && id.equals(((KeyboardLayout) other).id) && keys.equals(((KeyboardLayout) other).keys);
    }

    /** Hash consistent with {@link #equals}. */
    @Override
    public int hashCode() {
        return 31 * id.hashCode() + keys.hashCode();
    }

    /** The layout id. */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Creates a layout from {@code keys}, the characters on the physical keys in US QWERTY order: {@code q w e r t y
     * u i o p [ ] \}, then {@code a s d f g h j k l ; '}, then {@code z x c v b n m , .}, then a backtick and
     * {@code -}. That is exactly 35 distinct characters, lower case; {@code id} must not be blank.
     */
    @NotNull
    public static KeyboardLayout of(@NotNull String id, @NotNull String keys) {
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("Keyboard layout id must not be blank");
        }
        if (keys.length() != KEY_COUNT) {
            throw new IllegalArgumentException(
                    "Keyboard layout '" + id + "' needs exactly " + KEY_COUNT + " keys, not " + keys.length());
        }
        Set<Character> repeated = repeatedKeys(keys);
        if (!repeated.isEmpty()) {
            throw new IllegalArgumentException("Keyboard layout '" + id + "' repeats the key characters " + repeated);
        }
        return new KeyboardLayout(id, keys);
    }

    /**
     * Reads {@code text}, typed while {@code from} was active, as the keys would read under {@code to}. Case is kept
     * and characters that are not keys of {@code from} pass through unchanged.
     */
    @NotNull
    public static String convert(@NotNull String text, @NotNull KeyboardLayout from, @NotNull KeyboardLayout to) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int position = from.keys.indexOf(Character.toLowerCase(c));
            if (position < 0) {
                result.append(c);
            } else {
                char mapped = to.keys.charAt(position);
                result.append(Character.isUpperCase(c) ? Character.toUpperCase(mapped) : mapped);
            }
        }
        return result.toString();
    }

    /** The built-in layouts that contain every letter of {@code text}; empty when the text has no letters or mixes layouts. */
    @NotNull
    public static List<KeyboardLayout> detect(@NotNull String text) {
        return detect(text, builtIn);
    }

    /** The {@code layouts} that contain every letter of {@code text}; empty when the text has no letters or mixes layouts. */
    @NotNull
    public static List<KeyboardLayout> detect(@NotNull String text, @NotNull Collection<KeyboardLayout> layouts) {
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                letters.append(text.charAt(i));
            }
        }
        String lowered = letters.toString().toLowerCase(Locale.ROOT);
        List<KeyboardLayout> found = new ArrayList<KeyboardLayout>();
        if (lowered.isEmpty()) {
            return found;
        }
        for (KeyboardLayout layout : layouts) {
            if (layout.containsAll(lowered)) {
                found.add(layout);
            }
        }
        return found;
    }

    /** Alternatives over the built-in layouts; see {@link #alternatives(String, Collection)}. */
    @NotNull
    public static List<String> alternatives(@NotNull String text) {
        return alternatives(text, builtIn);
    }

    /**
     * Every distinct re-reading of {@code text} from each detected layout into each other of {@code layouts}, never
     * {@code text} itself.
     */
    @NotNull
    public static List<String> alternatives(@NotNull String text, @NotNull Collection<KeyboardLayout> layouts) {
        Set<String> distinct = new LinkedHashSet<String>();
        for (KeyboardLayout from : detect(text, layouts)) {
            for (KeyboardLayout to : layouts) {
                if (to.equals(from)) {
                    continue;
                }
                String converted = convert(text, from, to);
                if (!converted.equals(text)) {
                    distinct.add(converted);
                }
            }
        }
        return new ArrayList<String>(distinct);
    }

    private boolean containsAll(String letters) {
        for (int i = 0; i < letters.length(); i++) {
            if (keys.indexOf(letters.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static Set<Character> repeatedKeys(String keys) {
        Map<Character, Integer> counts = new LinkedHashMap<Character, Integer>();
        for (int i = 0; i < keys.length(); i++) {
            Integer seen = counts.get(keys.charAt(i));
            counts.put(keys.charAt(i), seen == null ? 1 : seen + 1);
        }
        Set<Character> repeated = new LinkedHashSet<Character>();
        for (Map.Entry<Character, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                repeated.add(entry.getKey());
            }
        }
        return repeated;
    }
}
