package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * High-level lifecycle of one typed configuration file.
 *
 * <p>A handle starts unloaded. {@link #load} or {@link #reload} parses, synchronizes, and validates the complete
 * document before replacing the in-memory value. Failed reloads leave the last valid value intact. {@link #unload}
 * only clears memory and never deletes or rewrites the YAML file.
 */
public interface ManagedConfig<T> extends AutoCloseable {
    /** Identifier {@code plugin/module:path}, or {@code null} for a library-internal file that no plugin registered. */
    @Nullable
    default ConfigId getId() {
        return null;
    }

    /** Read-only description of this file without any values from it, or {@code null} when {@link #getId()} is {@code null}. */
    @Nullable
    default ConfigInfo getInfo() {
        return null;
    }

    /**
     * The settings of this file that an administrator may change in game (fields marked {@link ConfigEditable}),
     * with their current values. Empty for a file registered outside a configuration scope.
     */
    @NotNull
    default List<ConfigSetting> getSettings() {
        return Collections.emptyList();
    }

    /**
     * Changes one setting by its dotted {@code path} from the typed {@code text}, validates the whole file and writes
     * it. When the new value is refused, the file and the value in memory stay as they were.
     *
     * @throws ConfigEditException when the path is unknown, the text does not fit, or validation fails
     */
    @NotNull
    default ConfigEditResult edit(@NotNull String path, @NotNull String text) {
        throw new UnsupportedOperationException("Settings of this configuration cannot be edited");
    }

    /**
     * Calls {@code listeners} after the file is read or written, or one of its settings is edited. A failing listener
     * is logged and does not stop the others. Closing the result removes them.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    default AutoCloseable onChanged(@NotNull Consumer<ConfigChange>... listeners) {
        return () -> { };
    }

    /**
     * Calls {@code listeners} when a setting that does not work at once ({@link ConfigApply#RELOAD},
     * {@link ConfigApply#RESTART}) was changed, so the plugin can apply it (usually by re-reading what it caches) or
     * tell the administrator to restart. Closing the result removes them.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    default AutoCloseable onReloadRequired(@NotNull Consumer<ReloadRequest>... listeners) {
        return () -> { };
    }

    /** Whether a configuration value is currently loaded in memory. */
    boolean isLoaded();

    /** Same as {@link #get()}; lets Kotlin code read the value as a property. */
    default T getValue() {
        return get();
    }

    /**
     * Returns the loaded value.
     *
     * @throws IllegalStateException before a successful {@link #load} or after {@link #unload}
     */
    T get();

    /**
     * Loads the file, applies migrations and synchronization policies, validates it, and stores the value in memory.
     * A missing file is handled according to {@link ConfigOptions#getMissingFile()}.
     */
    @NotNull
    ConfigLoadResult<T> load();

    /** Reloads the file while preserving the previous valid value on failure. */
    @NotNull
    ConfigLoadResult<T> reload();

    /** Loads the file and returns only its typed value. */
    default T loadValue() {
        return load().getValue();
    }

    /** Reloads the file and returns only its typed value. */
    default T reloadValue() {
        return reload().getValue();
    }

    /** Saves the current in-memory value after validation. */
    void save();

    /**
     * Validates and saves {@code value}, then makes it current.
     *
     * <p>The in-memory value is changed only after validation and persistence succeed.
     */
    void save(T value);

    /**
     * Applies {@code updater} to the current value, validates and persists the result, then returns it.
     *
     * <p>Implementations serialize concurrent calls. If the updater, validation, or write fails, the previously loaded
     * value remains current.
     */
    T update(@NotNull UnaryOperator<T> updater);

    /** Validates the current value without writing the file. */
    @NotNull
    List<ConfigProblem> validate();

    /** Replaces the file and current value with a freshly obtained code-defined default value. */
    T resetToDefaults();

    /** Clears the in-memory value while preserving the file on disk. */
    void unload();

    /** Alias for {@link #unload()} suitable for try-with-resources. */
    @Override
    default void close() {
        unload();
    }
}
