package org.pnfolder.pnlibrary.common.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WildcardsCacheTest {
    private final List<String> patterns = Arrays.asList("*", "", "shop-*", "*-addon", "a*b*c", "clan.v2", "Exact",
        "x*", "a\\Qb*", "[a-z]*", "(?i)q", "pn*lib*", "**", "é*");
    private final List<String> values = Arrays.asList("", "shop-main", "SHOP-", "eco-addon", "aXbYc", "clan.v2",
        "clanXv2", "EXACT", "xyz", "a\\Qbzz", "abc", "q", "pnlibrary", "PN-lib", "Éa", "[a-z]x");

    private static boolean reference(String pattern, String value) {
        String expression = Arrays.stream(pattern.split("\\*", -1))
            .map(Pattern::quote)
            .collect(Collectors.joining(".*"));
        return Pattern.compile("^" + expression + "$", Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    @Test
    void cachedMatchingEqualsCompilingOnEveryCall() {
        for (String pattern : patterns) {
            for (String value : values) {
                assertEquals(reference(pattern, value), Wildcards.matches(pattern, value), pattern + " / " + value);
            }
        }
    }

    @Test
    void aPatternIsCompiledOnceHoweverManyTimesItIsUsed() {
        List<String> unique = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            unique.add("cache-test-" + i + "-*");
        }
        int before = Wildcards.compilationCount();
        for (int i = 0; i < 100_000; i++) {
            Wildcards.matches(unique.get(i % unique.size()), "cache-test-" + (i % 7) + "-value");
        }
        assertEquals(unique.size(), Wildcards.compilationCount() - before);
    }
}
