package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Mutable YAML-like document exposed to {@link ConfigSteps} implementations.
 *
 * <p>Paths use dot-separated map keys, for example {@code database.pool.maximum}. Lists are values and cannot be
 * traversed by index. The constructor and {@link #toMap()} defensively copy nested maps and lists, so migration code
 * cannot mutate the parser's source data or the returned snapshot indirectly.
 */
public final class ConfigDocument {
    private final Map<String, Object> root;

    /** Creates a document holding a deep copy of {@code source}. */
    public ConfigDocument(@NotNull Map<String, Object> source) {
        this.root = mutableMap(source);
    }

    /** Returns whether {@code path} exists, including when its value is {@code null}. */
    public boolean contains(@NotNull String path) {
        return lookup(path) != MISSING;
    }

    /** Returns the value at {@code path}, or {@code null} when absent or explicitly null. */
    @Nullable
    public Object get(@NotNull String path) {
        Object found = lookup(path);
        return found == MISSING ? null : found;
    }

    /** Creates missing parent maps and stores a defensive copy of {@code value} at {@code path}. */
    @NotNull
    public ConfigDocument set(@NotNull String path, @Nullable Object value) {
        List<String> parts = parts(path);
        Map<String, Object> parent = parent(parts, true);
        parent.put(parts.get(parts.size() - 1), mutableValue(value));
        return this;
    }

    /** Removes and returns the value at {@code path}, or {@code null} when the path is absent. */
    @Nullable
    public Object remove(@NotNull String path) {
        List<String> parts = parts(path);
        return parent(parts, false).remove(parts.get(parts.size() - 1));
    }

    /** Moves an existing value from {@code from} to {@code to}, replacing the destination when necessary. */
    @NotNull
    public ConfigDocument move(@NotNull String from, @NotNull String to) {
        if (!contains(from)) {
            throw new IllegalArgumentException("Migration source path does not exist: " + from);
        }
        Object value = remove(from);
        set(to, value);
        return this;
    }

    /** Renames the final key component of {@code path} without moving it to another parent. */
    @NotNull
    public ConfigDocument rename(@NotNull String path, @NotNull String newName) {
        if (newName.indexOf('.') >= 0 || ConfigText.isBlank(newName)) {
            throw new IllegalArgumentException("New key must be one non-empty path component");
        }
        int dot = path.lastIndexOf('.');
        String parent = dot < 0 ? "" : path.substring(0, dot);
        return move(path, parent.isEmpty() ? newName : parent + "." + newName);
    }

    /** Replaces an existing value with the result returned by {@code transformer}. */
    @NotNull
    public ConfigDocument transform(@NotNull String path, @NotNull UnaryOperator<Object> transformer) {
        if (!contains(path)) {
            throw new IllegalArgumentException("Migration path does not exist: " + path);
        }
        return set(path, transformer.apply(get(path)));
    }

    /** Returns an immutable deep snapshot of this document. */
    @NotNull
    public Map<String, Object> toMap() {
        return immutableMap(root);
    }

    /** Marks a path that does not exist, so that an existing {@code null} value stays distinguishable. */
    private static final Object MISSING = new Object();

    private Object lookup(String path) {
        Object current = root;
        for (String part : parts(path)) {
            if (!(current instanceof Map)) {
                return MISSING;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(part)) {
                return MISSING;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parent(List<String> parts, boolean create) {
        Map<String, Object> current = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            Object next = current.get(part);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else if (next == null) {
                if (!create) {
                    return new LinkedHashMap<>();
                }
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(part, created);
                current = created;
            } else {
                throw new IllegalArgumentException("Path component is not an object: " + part);
            }
        }
        return current;
    }

    private static List<String> parts(String path) {
        String[] split = path.split("\\.", -1);
        for (String part : split) {
            if (ConfigText.isBlank(part)) {
                throw new IllegalArgumentException("Invalid configuration path: " + path);
            }
        }
        List<String> parts = new ArrayList<>(split.length);
        Collections.addAll(parts, split);
        return parts;
    }

    private static Map<String, Object> mutableMap(Map<String, Object> value) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            out.put(entry.getKey(), mutableValue(entry.getValue()));
        }
        return out;
    }

    private static Object mutableValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(entry.getKey()), mutableValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<?>) value) {
                out.add(mutableValue(item));
            }
            return out;
        }
        return value;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            out.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(entry.getKey()), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<?>) value) {
                out.add(immutableValue(item));
            }
            return Collections.unmodifiableList(out);
        }
        return value;
    }
}
