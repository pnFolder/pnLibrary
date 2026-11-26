package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigRedaction;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticRedactor;

class ConfigRedactionTest {
    private static ConfigRedaction redaction(Set<String> excluded, List<Pattern> keys, List<Pattern> values) {
        return new ConfigRedaction(excluded, keys, values, new DiagnosticRedactor());
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    void excludedPathAndSecretKeyAreHiddenInRequestedAndRedact() {
        Map<String, Object> data = map("db", map("pass", "hunter2", "host", "h"), "api", "tok", "name", "n");
        ConfigRedaction redaction = redaction(
            Collections.singleton("db.pass"),
            ConfigRedaction.compile(Collections.singletonList("^api$")),
            Collections.<Pattern>emptyList());

        for (Map<String, Object> result : Arrays.asList(redaction.requested(data), redaction.redact(data))) {
            assertFalse(result.toString().contains("hunter2"), result.toString());
            assertFalse(result.toString().contains("tok"), result.toString());
            assertEquals("n", result.get("name"));
        }
    }

    @Test
    void valuePatternReplacesTheMatchInRequestedValues() {
        ConfigRedaction redaction = redaction(
            Collections.<String>emptySet(),
            Collections.<Pattern>emptyList(),
            Collections.singletonList(Pattern.compile("leak")));

        Map<String, Object> result = redaction.requested(map("note", "a leak here"));

        assertFalse(result.get("note").toString().contains("leak"));
    }

    @Test
    void wildcardPathHidesASecretInsideEveryValueOfAMapAndEveryListItem() {
        Map<String, Object> data = map(
            "accounts", map("alice", map("pin", "1111", "name", "A"), "bob", map("pin", "2222")),
            "users", Collections.singletonList(map("pin", "3333", "name", "C")));
        ConfigRedaction redaction = redaction(
            new HashSet<String>(Arrays.asList("accounts.*.pin", "users.pin")),
            Collections.<Pattern>emptyList(),
            Collections.<Pattern>emptyList());

        for (Map<String, Object> result : Arrays.asList(redaction.requested(data), redaction.redact(data))) {
            String text = result.toString();
            for (String secret : Arrays.asList("1111", "2222", "3333")) {
                assertFalse(text.contains(secret), text);
            }
            assertTrue(text.contains("name=A") && text.contains("name=C"), text);
        }
    }
}
