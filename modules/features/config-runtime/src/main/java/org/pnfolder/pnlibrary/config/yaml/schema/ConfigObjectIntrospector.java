package org.pnfolder.pnlibrary.config.yaml.schema;

import org.pnfolder.pnlibrary.config.yaml.ConfigNaming;
import org.pnfolder.pnlibrary.config.yaml.text.YamlText;
import org.pnfolder.pnlibrary.config.yaml.database.DatabasePathScanner;
import org.pnfolder.pnlibrary.config.yaml.database.DatabasePath;
import java.util.Objects;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.BackupDatabase;
import org.pnfolder.pnlibrary.api.config.ConfigIgnore;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.config.ConfigNamingStrategy;
import org.pnfolder.pnlibrary.api.config.ConfigOrder;
import org.pnfolder.pnlibrary.api.config.ConfigSecret;

/**
 * Central reflection policy for annotated mutable configuration objects.
 *
 * <p>Field discovery, key naming, raw generic-type resolution, object construction,
 * and collection materialization live here so every encode/decode/schema path uses
 * identical reflection rules.
 */
public final class ConfigObjectIntrospector {
    /** How many classes had their declared fields read since start; tests use it to prove the cache works. */
    public static final AtomicInteger DECLARED_FIELD_READS = new AtomicInteger();

    private static final int MAX_SECRET_DEPTH = 8;
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<List<Field>>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            return discover(type);
        }
    };

    private final ConfigNamingStrategy defaultNaming;

    /** @param defaultNaming naming strategy applied to fields without an explicit key */
    public ConfigObjectIntrospector(@NotNull ConfigNamingStrategy defaultNaming) {
        this.defaultNaming = Objects.requireNonNull(defaultNaming, "defaultNaming");
    }

    /** Returns serializable fields in superclass-first, configured order; read once per class, not per call. */
    @NotNull
    public List<Field> fields(@NotNull Class<?> type) {
        return FIELDS.get(type);
    }

    /** The YAML key of {@code field}: {@code @ConfigKey} if present, otherwise the field name in the configured naming style. */
    @NotNull
    public String key(@NotNull Field field) {
        ConfigKey explicit = field.getAnnotation(ConfigKey.class);
        if (explicit != null && !YamlText.isBlank(explicit.value())) {
            return explicit.value();
        }
        return ConfigNaming.apply(field.getName(), defaultNaming);
    }

    /** Returns the dotted YAML paths of every {@code @ConfigSecret} field of {@code type}, including those of nested sections. */
    @NotNull
    public List<String> secretPaths(@NotNull Class<?> type) {
        List<String> paths = new ArrayList<String>();
        collectSecrets(type, "", 0, paths);
        return paths;
    }

    private void collectSecrets(Class<?> type, String prefix, int depth, List<String> out) {
        if (depth >= MAX_SECRET_DEPTH) {
            return;
        }
        for (Field field : fields(type)) {
            String path = prefix + key(field);
            if (field.isAnnotationPresent(ConfigSecret.class)) {
                out.add(path);
                continue;
            }
            collectNestedSecrets(field.getGenericType(), path, depth, out);
        }
    }

    /** Lists add no segment to a report path; a map value adds its key, written {@code *} because it is only known at run time. */
    private void collectNestedSecrets(Type type, String path, int depth, List<String> out) {
        Class<?> raw = rawClass(type);
        Type[] arguments = type instanceof ParameterizedType ? ((ParameterizedType) type).getActualTypeArguments() : new Type[0];
        if (Collection.class.isAssignableFrom(raw)) {
            if (arguments.length > 0) {
                collectNestedSecrets(arguments[0], path, depth + 1, out);
            }
        } else if (Map.class.isAssignableFrom(raw)) {
            if (arguments.length > 1) {
                collectNestedSecrets(arguments[1], path + ".*", depth + 1, out);
            }
        } else if (isSection(raw) && depth < MAX_SECRET_DEPTH) {
            collectSecrets(raw, path + ".", depth + 1, out);
        }
    }

    /** Whether {@code raw} is a nested object of the plugin's own, not a scalar, array, enum or library type. */
    public boolean isSection(@NotNull Class<?> raw) {
        return !raw.isPrimitive() && !raw.isEnum() && !raw.isArray() && !isScalar(raw)
                && !raw.getName().startsWith("java.") && !raw.getName().startsWith("kotlin.");
    }

    /** The databases marked {@code @BackupDatabase} in {@code type} (the class may carry several), with the dotted YAML path of each. */
    @NotNull
    public List<DatabasePath> databasePaths(@NotNull Class<?> type) {
        return new DatabasePathScanner(this).scan(type);
    }

    /** Reads the current value of {@code field} from {@code owner}. */
    @Nullable
    public Object read(@NotNull Field field, @NotNull Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Resolves the concrete raw class represented by {@code type}, or {@code Object} if unknown. */
    @NotNull
    public Class<?> rawClass(@NotNull Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            return raw instanceof Class ? (Class<?>) raw : Object.class;
        }
        if (type instanceof WildcardType) {
            Type[] bounds = ((WildcardType) type).getUpperBounds();
            return bounds.length > 0 ? rawClass(bounds[0]) : Object.class;
        }
        return Object.class;
    }

    /** Whether {@code type} can be represented directly as one YAML scalar. */
    public boolean isScalar(@NotNull Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type == String.class
                || Number.class.isAssignableFrom(type) || type == java.lang.Boolean.class;
    }

    /**
     * Creates {@code type} through an accessible no-argument constructor.
     *
     * @throws IllegalArgumentException with configuration {@code path} context when the type cannot be instantiated
     */
    @NotNull
    public Object instantiate(@NotNull Class<?> type, @NotNull String path) {
        try {
            java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Type " + type.getName() + " at " + path + " needs a no-argument constructor or a ConfigSerializer", exception);
        }
    }

    /** Builds the collection of declared {@code type} holding {@code values}; an interface or abstract type gets a standard one. */
    @NotNull
    @SuppressWarnings("unchecked")
    public Collection<Object> collection(@NotNull Class<?> type, @NotNull Collection<Object> values, @NotNull String path) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            if (SortedSet.class.isAssignableFrom(type)) {
                return new TreeSet<Object>(values);
            }
            if (Set.class.isAssignableFrom(type)) {
                return new LinkedHashSet<Object>(values);
            }
            if (Queue.class.isAssignableFrom(type)) {
                return new LinkedList<Object>(values);
            }
            return new ArrayList<Object>(values);
        }
        Object created = instantiate(type, path);
        if (!(created instanceof Collection)) {
            throw new IllegalArgumentException("Type " + type.getName() + " at " + path + " is not a mutable collection");
        }
        Collection<Object> collection = (Collection<Object>) created;
        collection.addAll(values);
        return collection;
    }

    /** Builds the map of declared {@code type} holding {@code values}; an interface or abstract type gets a standard one. */
    @NotNull
    @SuppressWarnings("unchecked")
    public Map<Object, Object> map(@NotNull Class<?> type, @NotNull Map<Object, Object> values, @NotNull String path) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return SortedMap.class.isAssignableFrom(type) ? new TreeMap<Object, Object>(values)
                    : new LinkedHashMap<Object, Object>(values);
        }
        Object created = instantiate(type, path);
        if (!(created instanceof Map)) {
            throw new IllegalArgumentException("Type " + type.getName() + " at " + path + " is not a mutable map");
        }
        Map<Object, Object> map = (Map<Object, Object>) created;
        map.putAll(values);
        return map;
    }

    private static List<Field> discover(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> current : hierarchy) {
            DECLARED_FIELD_READS.incrementAndGet();
            for (Field field : current.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())
                        && !Modifier.isTransient(field.getModifiers()) && !field.isAnnotationPresent(ConfigIgnore.class)) {
                    fields.add(field);
                }
            }
        }
        Collections.sort(fields, Comparator.comparingInt(ConfigObjectIntrospector::orderOf));
        for (Field field : fields) {
            field.setAccessible(true);
        }
        return Collections.unmodifiableList(fields);
    }

    private static int orderOf(Field field) {
        ConfigOrder order = field.getAnnotation(ConfigOrder.class);
        return order == null ? 0 : order.value();
    }
}
