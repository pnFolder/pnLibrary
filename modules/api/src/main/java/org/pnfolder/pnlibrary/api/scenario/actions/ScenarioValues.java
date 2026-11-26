package org.pnfolder.pnlibrary.api.scenario.actions;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** The named values one scenario run knows, which texts and requirements read as {@code [name]}. */
final class ScenarioValues {
    private final Map<String, Object> values;

    ScenarioValues(Map<String, Object> initial) {
        this.values = new LinkedHashMap<>(initial);
    }

    /** The live map, for readers that must see later changes. */
    Map<String, Object> live() {
        return values;
    }

    void put(String name, Object value) {
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Value name must not be blank");
        }
        values.put(name, value);
    }

    Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    <T> T scoped(Map<String, Object> extra, Supplier<T> block) {
        Map<String, Object> saved = new LinkedHashMap<>();
        Set<String> had = new HashSet<>();
        for (String key : extra.keySet()) {
            saved.put(key, values.get(key));
            if (values.containsKey(key)) {
                had.add(key);
            }
        }
        values.putAll(extra);
        try {
            return block.get();
        } finally {
            for (String key : extra.keySet()) {
                if (had.contains(key)) {
                    values.put(key, saved.get(key));
                } else {
                    values.remove(key);
                }
            }
        }
    }
}
