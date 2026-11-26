package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Typed YAML configuration files and serializers tied to one plugin lifecycle.
 *
 * <pre>{@code
 * ManagedConfig<Settings> settings = library.getConfigurations().scope(plugin).yaml("config.yml", Settings.class, Settings::new);
 * Settings loaded = settings.load().getValue();
 * }</pre>
 *
 * <p>Closing the scope unloads every managed file and removes its dynamically published types. It never deletes
 * configuration files.
 */
public interface ConfigScope extends AutoCloseable {
    /** Registers a {@code .yml} or {@code .yaml} file using {@link ConfigOptions#DEFAULT}. */
    @NotNull
    <T> ManagedConfig<T> yaml(@NotNull String path, @NotNull Class<T> type, @NotNull Supplier<T> defaults);

    /**
     * Registers a typed YAML file relative to the plugin data directory.
     *
     * <p>{@code path} must be relative, remain inside the data directory after normalization, and end in
     * {@code .yml} or {@code .yaml}. One scope cannot register the same normalized file twice. {@code defaults} is
     * evaluated while the handle is created and must return a non-null value.
     */
    @NotNull
    default <T> ManagedConfig<T> yaml(@NotNull String path, @NotNull Class<T> type, @NotNull Supplier<T> defaults,
                                      @NotNull ConfigOptions options) {
        return yaml(path, type, defaults, options, value -> Collections.emptyList());
    }

    /**
     * Registers a file at any place on the machine, for example a path the plugin read from its own settings.
     * The file is the plugin's own: it is created when missing and rewritten like one in the data folder.
     * Its identifier is {@code plugin/module:@external/<absolute path>}.
     */
    @NotNull
    default <T> ManagedConfig<T> yamlAt(@NotNull Path file, @NotNull Class<T> type, @NotNull Supplier<T> defaults) {
        return yamlAt(file, type, defaults, ConfigOptions.DEFAULT);
    }

    /** Registers a file at any place on the machine with explicit synchronization {@code options}. */
    @NotNull
    default <T> ManagedConfig<T> yamlAt(@NotNull Path file, @NotNull Class<T> type, @NotNull Supplier<T> defaults,
                                        @NotNull ConfigOptions options) {
        return yaml(file.toAbsolutePath().normalize().toString(), type, defaults,
                options.toBuilder().root(ConfigRoot.EXTERNAL).build());
    }

    /**
     * Registers a typed YAML file with an additional programmatic {@code validator}.
     *
     * <p>Annotation rules such as {@code @ConfigRange} and the validator both run on every load, reload and save;
     * their problems are reported together in one {@link ConfigValidationException}.
     */
    @NotNull
    <T> ManagedConfig<T> yaml(@NotNull String path, @NotNull Class<T> type, @NotNull Supplier<T> defaults,
                              @NotNull ConfigOptions options, @NotNull ConfigValueValidator<T> validator);

    /**
     * Registers or replaces the scope-local serializer for {@code type}.
     *
     * <p>It works in the files of this module only; use {@link #serializerFor} to work in other files. A file is read
     * with the serializers it has at the time of the read.
     */
    @NotNull
    <T> ConfigScope serializer(@NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer);

    /**
     * Starts publishing {@code serializer} for {@code type}, to work in this module or, with {@code into}, in other
     * files.
     *
     * <pre>{@code
     * // Every file of the plugin `shop` writes a Point as one line
     * scope.serializerFor(Point.class, new PointSerializer()).into("shop").register();
     *
     * // Every file of every plugin
     * scope.serializerFor(Point.class, new PointSerializer()).everywhere().register();
     * }</pre>
     *
     * <p>The file's own module keeps its serializers: an addition only fills what the module has none for. Files that
     * are already read keep their value until read again. Nothing is published until
     * {@link ConfigSerializerBuilder#register} is called.
     */
    @NotNull
    default <T> ConfigSerializerBuilder<T> serializerFor(@NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer) {
        return new ConfigSerializerBuilder<>(this, type, serializer);
    }

    /**
     * Low-level publication behind {@link #serializerFor}; prefer the builder.
     *
     * <p>{@code target} {@code null} means the files of this scope's module. For files of other modules the type must
     * not be a primitive or come from {@code java.}, {@code javax.}, {@code kotlin.} or {@code jdk.}, so that nobody
     * rewrites how ordinary values are read.
     */
    @NotNull
    <T> ConfigSerializerRegistration publishSerializer(@NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer,
                                                       int priority, @Nullable ConfigAddress target);

    /**
     * Starts publishing one implementation of a polymorphic configuration {@code baseType}.
     *
     * <pre>{@code
     * // Only my own files understand `type: message`
     * scope.type(Action.class, MessageAction.class).name("message").alias("msg").register();
     *
     * // Teach another plugin's file the word: `type: message` works in quests/main:quests.yml
     * scope.type(Action.class, MessageAction.class).name("message").into("quests/main:quests.yml").register();
     * }</pre>
     *
     * <p>Nothing is published until {@link ConfigTypeBuilder#register} is called.
     */
    @NotNull
    default <T> ConfigTypeBuilder<T> type(@NotNull Class<T> baseType, @NotNull Class<? extends T> implementation) {
        return new ConfigTypeBuilder<>(this, baseType, implementation);
    }

    /** Low-level publication behind {@link #type}; a clash with an existing word fails. */
    @NotNull
    default <T> ConfigTypeRegistration publishType(@NotNull Class<T> baseType, @NotNull Class<? extends T> implementation,
                                                   @NotNull String name, @NotNull Set<String> aliases, int priority,
                                                   @Nullable ConfigAddress target) {
        return publishType(baseType, implementation, name, aliases, priority, target, ConfigTypeConflict.FAIL);
    }

    /**
     * Low-level publication behind {@link #type}; prefer the builder.
     *
     * <p>{@code name} and {@code aliases} are case-insensitive identifiers used in the discriminator field. Higher
     * {@code priority} wins when multiple visible registrations accept the same identifier. {@code target} says where
     * the word works; {@code null} means the files of this scope's module. The word cannot take a name that the base
     * type declares itself or that the target plugin has published.
     */
    @NotNull
    <T> ConfigTypeRegistration publishType(@NotNull Class<T> baseType, @NotNull Class<? extends T> implementation,
                                           @NotNull String name, @NotNull Set<String> aliases, int priority,
                                           @Nullable ConfigAddress target, @NotNull ConfigTypeConflict conflict);

    /** Plugin that owns this scope. */
    @NotNull
    PluginId getPlugin();

    /** Module that owns this scope. */
    @NotNull
    ModuleId getModule();

    /** The files registered in this scope, in registration order. */
    @NotNull
    List<ConfigInfo> list();

    /** The file registered under {@code path} (relative to the plugin folder, or starting with {@code @plugins/} or {@code @server/}), or {@code null}. */
    @Nullable
    ManagedConfig<?> get(@NotNull String path);

    /** The file registered under {@code path}, or an error naming the registered files. */
    @NotNull
    default ManagedConfig<?> require(@NotNull String path) {
        ManagedConfig<?> config = get(path);
        if (config != null) {
            return config;
        }
        StringBuilder registered = new StringBuilder();
        for (ConfigInfo info : list()) {
            if (registered.length() > 0) {
                registered.append(", ");
            }
            registered.append(info.getId().getPath());
        }
        throw new IllegalStateException("Configuration " + path + " is not registered in " + getPlugin() + "/"
                + getModule() + "; registered: " + registered);
    }

    /** Loads every registered file in registration order. */
    void loadAll();

    /** Reloads every registered file while each handle preserves its prior value on failure. */
    void reloadAll();

    /** Saves every currently loaded file in registration order. */
    void saveAll();

    /** Number of managed YAML handles currently registered in this scope. */
    int getSize();

    /** Unloads all files and removes dynamic types owned by this scope. */
    @Override
    void close();
}
