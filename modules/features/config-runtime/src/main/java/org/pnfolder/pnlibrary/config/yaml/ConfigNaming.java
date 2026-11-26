package org.pnfolder.pnlibrary.config.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;

/** Turns a field name into the YAML key of a naming style. */
final class ConfigNaming {
    private static final Pattern LOWER_TO_UPPER_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern ACRONYM_BOUNDARY = Pattern.compile("([A-Z]+)([A-Z][a-z])");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[_\\-\\s]+");

    private ConfigNaming() {
    }

    @NotNull
    static String apply(@NotNull String value, @NotNull ConfigNamingStrategy strategy) {
        if (strategy == ConfigNamingStrategy.AS_DECLARED) {
            return value;
        }
        String spaced = ACRONYM_BOUNDARY.matcher(LOWER_TO_UPPER_BOUNDARY.matcher(value).replaceAll("$1 $2")).replaceAll("$1 $2");
        List<String> words = new ArrayList<String>();
        for (String word : WORD_SEPARATOR.split(spaced)) {
            if (!YamlText.isBlank(word)) {
                words.add(word);
            }
        }
        if (words.isEmpty()) {
            throw new NoSuchElementException("List is empty.");
        }
        switch (strategy) {
            case CAMEL_CASE:
                StringBuilder camel = new StringBuilder(words.get(0).toLowerCase(Locale.ROOT));
                for (String word : words.subList(1, words.size())) {
                    String lower = word.toLowerCase(Locale.ROOT);
                    camel.append(lower.substring(0, 1).toUpperCase(Locale.ROOT)).append(lower.substring(1));
                }
                return camel.toString();
            case SNAKE_CASE:
                return join(words, "_", false);
            case KEBAB_CASE:
                return join(words, "-", false);
            case UPPER_SNAKE_CASE:
                return join(words, "_", true);
            default:
                return value;
        }
    }

    private static String join(List<String> words, String separator, boolean upper) {
        StringBuilder joined = new StringBuilder();
        for (String word : words) {
            if (joined.length() > 0) {
                joined.append(separator);
            }
            joined.append(upper ? word.toUpperCase(Locale.ROOT) : word.toLowerCase(Locale.ROOT));
        }
        return joined.toString();
    }
}
