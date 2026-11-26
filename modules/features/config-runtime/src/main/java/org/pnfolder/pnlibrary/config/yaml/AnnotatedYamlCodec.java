package org.pnfolder.pnlibrary.config.yaml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigCodec;
import org.pnfolder.pnlibrary.api.config.ConfigComment;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.UnknownTypePolicy;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Reflection-based YAML codec for mutable Java/Kotlin configuration objects.
 *
 * <p>Decoding starts from a fresh {@code defaults} instance, preserving field defaults for
 * absent YAML keys. Fields may use custom serializers, validation annotations,
 * naming policies, generated comments, and polymorphic implementations. Static,
 * transient, synthetic, and {@code @ConfigIgnore} fields are excluded.
 *
 * <p>Configuration classes must expose mutable backing fields. Nested concrete
 * objects and annotation serializers require a no-argument constructor unless a
 * registered {@link ConfigSerializer} handles the type.
 */
public final class AnnotatedYamlCodec<T> implements ConfigCodec<T>, ConfigSchema {
    private final Class<T> type;
    private final Supplier<T> defaults;
    private final ConfigObjectIntrospector introspector;
    private final ConfigSerializerResolver serializerResolver;
    private final ConfigSchemaInspector schemaInspector;
    private final YamlValueWriter writer;
    private final YamlValueReader reader;
    private final Set<String> requiredPaths;
    private final List<String> secretPaths;
    private final List<DatabasePath> databases;
    private final Yaml yaml;
    private volatile List<String> skippedUnknownTypes = Collections.emptyList();

    /**
     * Creates a codec.
     *
     * @param type root configuration class
     * @param defaults factory for a pristine root object on every decode
     * @param serializers the serializers keyed by supported Java type, asked again on every use so that additions count
     * @param runtimeTypes current external polymorphic type registrations
     * @param consumer plugin namespace resolving unqualified external types
     * @param options naming and document-generation policy
     * @param warning receiver for values replaced via {@code @ConfigDefaultOnInvalid}
     */
    public AnnotatedYamlCodec(@NotNull Class<T> type, @NotNull Supplier<T> defaults,
                              @NotNull Supplier<Map<Class<?>, ConfigSerializer<?>>> serializers,
                              @NotNull Supplier<List<RuntimeConfigType>> runtimeTypes, @NotNull String consumer,
                              @NotNull ConfigOptions options, @NotNull Consumer<String> warning) {
        this.type = type;
        this.defaults = defaults;
        this.introspector = new ConfigObjectIntrospector(options.getNaming());
        this.serializerResolver = new ConfigSerializerResolver(serializers);
        this.schemaInspector = new ConfigSchemaInspector(introspector, serializerResolver);
        ConfigPolymorphicResolver polymorphic = new ConfigPolymorphicResolver(runtimeTypes, consumer, introspector);
        this.writer = new YamlValueWriter(introspector, serializerResolver, polymorphic, this::context);
        this.reader = new YamlValueReader(introspector, serializerResolver, polymorphic, schemaInspector, options, warning, this::context);
        this.requiredPaths = schemaInspector.requiredPaths(type);
        this.secretPaths = introspector.secretPaths(type);
        this.databases = introspector.databasePaths(type);
        this.yaml = createYaml();
    }

    private static Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(false);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setWidth(120);
        return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options);
    }

    @Override
    @NotNull
    public Set<String> getRequiredPaths() {
        return requiredPaths;
    }

    /** Dotted paths of the {@code @ConfigSecret} fields, which diagnostic reports never show. */
    @NotNull
    public List<String> getSecretPaths() {
        return secretPaths;
    }

    /** The sections marked {@code @BackupDatabase}; the update system copies those databases. */
    @NotNull
    public List<DatabasePath> getDatabases() {
        return databases;
    }

    /**
     * List elements the last {@link #decode} skipped because their {@code type} is unknown (only with
     * {@link UnknownTypePolicy#SKIP}). Each entry names the element and the word.
     */
    @NotNull
    public List<String> getSkippedUnknownTypes() {
        return skippedUnknownTypes;
    }

    /** Writes {@code value} as YAML with the comments, blank lines and file header declared by annotations. */
    @Override
    @NotNull
    public String encode(@NotNull T value) {
        String raw = yaml.dump(writer.write(value, type, "", Collections.<Annotation>emptyList(), value));
        String body = YamlSchemaDecorator.decorate(raw, schemaInspector.metadata(type, value));
        ConfigComment header = type.getAnnotation(ConfigComment.class);
        if (header == null || header.value().length == 0) {
            return body;
        }
        return YamlSchemaDecorator.commentLines(header.value(), "") + "\n\n" + body;
    }

    /**
     * Decodes {@code yaml} into a fresh defaults instance.
     *
     * @throws IllegalArgumentException for incompatible values, missing or ambiguous polymorphic types, and classes that
     *         cannot be instantiated
     */
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public T decode(@NotNull String yaml) {
        reader.startRead();
        try {
            Object loaded = this.yaml.load(yaml);
            if (loaded == null) {
                loaded = new LinkedHashMap<String, Object>();
            }
            ConfigSerializer<Object> serializer = serializerResolver.forType(type);
            if (serializer != null) {
                return (T) serializer.deserialize(loaded, context(type, "", Collections.<Annotation>emptyList(), defaults.get()));
            }
            if (!(loaded instanceof Map)) {
                throw new IllegalArgumentException("Root value of " + type.getName() + " must be a YAML object");
            }
            T created = defaults.get();
            reader.populate(created, type, (Map<?, ?>) loaded, "");
            return created;
        } finally {
            skippedUnknownTypes = reader.skippedSoFar();
        }
    }

    /** Evaluates declarative validation annotations without mutating {@code value}. */
    @NotNull
    public List<ConfigProblem> validate(@NotNull T value) {
        return schemaInspector.validate(value, type);
    }

    private ConfigSerializationContext context(Type declaredType, String path, List<Annotation> annotations, Object defaultValue) {
        return new ConfigSerializationContext(path, declaredType, introspector.rawClass(declaredType), annotations, defaultValue,
                (value, valueType, at) -> reader.decodeNested(value, valueType, at),
                (value, valueType, at) -> writer.write(value, valueType, at));
    }
}
