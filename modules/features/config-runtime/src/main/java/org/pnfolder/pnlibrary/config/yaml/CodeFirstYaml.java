package org.pnfolder.pnlibrary.config.yaml;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigCodec;
import org.pnfolder.pnlibrary.api.config.ConfigLoadResult;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigSteps;
import org.pnfolder.pnlibrary.api.config.ConfigValidationException;
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.config.MissingFilePolicy;
import org.pnfolder.pnlibrary.api.config.MissingValuePolicy;
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

/**
 * Loads typed YAML and synchronizes it with code-defined defaults.
 *
 * <p>Missing keys and their comments are added recursively. Existing values,
 * ordering, comments, and extension keys are preserved. A {@code .before-sync-*.bak}
 * copy is made before a rewrite when {@link ConfigOptions#getBackups()} is on, and the target
 * is replaced atomically only after successful decoding.
 *
 * <pre>{@code
 * Settings settings = new CodeFirstYaml<Settings>(file, new Settings(), codec, logger).load().getValue();
 * }</pre>
 */
public final class CodeFirstYaml<T> implements ManagedConfig<T> {
    private final File file;
    private final T defaults;
    private final ConfigCodec<T> codec;
    private final PnLogger logger;
    private final ConfigValueValidator<T> validator;
    private final ConfigOptions options;
    private final YamlMigrationEngine migrationEngine = new YamlMigrationEngine();
    private final ConfigurationFileStore fileStore;
    private volatile T loadedValue;
    private volatile List<String> skippedUnknownTypes = Collections.emptyList();

    /** Creates a handle that checks nothing beyond decoding and uses {@link ConfigOptions#DEFAULT}. */
    public CodeFirstYaml(@NotNull File file, @NotNull T defaults, @NotNull ConfigCodec<T> codec, @NotNull PnLogger logger) {
        this(file, defaults, codec, logger, value -> Collections.<ConfigProblem>emptyList(), ConfigOptions.DEFAULT);
    }

    /** Creates a handle that uses {@link ConfigOptions#DEFAULT}. */
    public CodeFirstYaml(@NotNull File file, @NotNull T defaults, @NotNull ConfigCodec<T> codec, @NotNull PnLogger logger,
                         @NotNull ConfigValueValidator<T> validator) {
        this(file, defaults, codec, logger, validator, ConfigOptions.DEFAULT);
    }

    /**
     * Creates a handle.
     *
     * @param validator semantic checks run on every decoded or saved value
     */
    public CodeFirstYaml(@NotNull File file, @NotNull T defaults, @NotNull ConfigCodec<T> codec, @NotNull PnLogger logger,
                         @NotNull ConfigValueValidator<T> validator, @NotNull ConfigOptions options) {
        this.file = file;
        this.defaults = defaults;
        this.codec = codec;
        this.logger = logger;
        this.validator = validator;
        this.options = options;
        this.fileStore = new ConfigurationFileStore(file, content -> codec.decode(content));
    }

    /** {@code true} after a successful {@link #load} or {@link #reload} and before {@link #unload}. */
    @Override
    public boolean isLoaded() {
        return loadedValue != null;
    }

    /** Returns the loaded value or fails when the file is not loaded. */
    @Override
    public T get() {
        T value = loadedValue;
        if (value == null) {
            throw new IllegalStateException("Configuration " + file.getName() + " is not loaded");
        }
        return value;
    }

    /**
     * List elements skipped at the last read because their {@code type} is unknown, empty when nothing was skipped.
     * While it is not empty, saving is refused: the elements exist only in the file.
     */
    @NotNull
    public List<String> getSkippedUnknownTypes() {
        return skippedUnknownTypes;
    }

    /** Creates a missing file, adds new fields to an existing file, validates the value, and returns synchronization details. */
    @Override
    @NotNull
    public synchronized ConfigLoadResult<T> load() {
        try {
            return loadFile();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private ConfigLoadResult<T> loadFile() throws IOException {
        if (options.getReadOnly()) {
            return loadWithoutWriting();
        }
        // Nothing may be created for a file that must already exist, not even its folder.
        if (!file.exists() && options.getMissingFile() != MissingFilePolicy.CREATE) {
            throw new IllegalStateException("Configuration " + file.getName() + " does not exist");
        }
        File parent = file.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalArgumentException("Cannot create configuration directory " + parent);
        }
        String rawDefaultsYaml = fileStore.normalize(codec.encode(defaults));
        ConfigSteps<?> migrations = options.getMigrations();
        String defaultsYaml = migrations != null
                ? fileStore.normalize(migrationEngine.stampDefaults(rawDefaultsYaml, migrations)) : rawDefaultsYaml;
        if (!file.exists()) {
            return createFromDefaults(defaultsYaml);
        }
        String original = fileStore.normalize(readFile());
        YamlMigrationEngine.Result migration = migrations != null ? migrationEngine.migrate(original, migrations) : null;
        if (migration != null && migration.isNewerThanCode()) {
            return readNewerFile(original);
        }
        // Re-dumped YAML loses administrator comments, so it is used only when a migration step ran.
        boolean migrated = migration != null && !migration.getApplied().isEmpty();
        String working = migrated ? fileStore.normalize(migration.getContent()) : original;
        List<String> appliedMigrations = migration != null ? migration.getApplied() : Collections.<String>emptyList();
        Differences differences = differences(defaultsYaml, working);
        YamlDefaultsMerger.Result syncResult = synchronize(working, defaultsYaml);
        String synchronizedContent = LegacySupportNote.removeFrom(syncResult.getContent());
        T value = decodeAndValidate(synchronizedContent);
        if (synchronizedContent.equals(original)) {
            loadedValue = value;
            return new ConfigLoadResult<T>(value, Collections.<String>emptyList(), null, Collections.<String>emptyList(),
                    Collections.<String>emptyList(), appliedMigrations);
        }
        return writeChanges(original, synchronizedContent, value, differences, appliedMigrations, syncResult);
    }

    private String readFile() throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** A read-only file is read as it is: nothing is created, migrated, synchronized or backed up. */
    private ConfigLoadResult<T> loadWithoutWriting() throws IOException {
        if (!file.exists()) {
            throw new IllegalStateException("Configuration " + file.getName() + " does not exist");
        }
        T value = decodeAndValidate(fileStore.normalize(readFile()));
        loadedValue = value;
        return new ConfigLoadResult<T>(value, Collections.<String>emptyList(), null);
    }

    private ConfigLoadResult<T> createFromDefaults(String defaultsYaml) throws IOException {
        if (options.getMissingFile() != MissingFilePolicy.CREATE) {
            throw new IllegalStateException("Configuration " + file.getName() + " does not exist");
        }
        T value = decodeAndValidate(defaultsYaml);
        fileStore.writeDecoded(defaultsYaml);
        loadedValue = value;
        return new ConfigLoadResult<T>(value, YamlDefaultsMerger.paths(defaultsYaml), null);
    }

    /** A file written by a newer plugin version is read as it is and never rewritten. */
    private ConfigLoadResult<T> readNewerFile(String original) {
        logger.warning("Файл " + file.getName() + " создан более новой версией плагина; он читается без изменений.");
        T value = decodeAndValidate(original);
        loadedValue = value;
        return new ConfigLoadResult<T>(value, Collections.<String>emptyList(), null);
    }

    /** Paths missing from the file and paths unknown to the code. */
    private static final class Differences {
        final List<String> missing;
        final List<String> unknown;

        Differences(List<String> missing, List<String> unknown) {
            this.missing = missing;
            this.unknown = unknown;
        }
    }

    /** The paths the file lacks and the paths the code does not know; fails when the policy forbids either. */
    private Differences differences(String defaultsYaml, String working) {
        Set<String> defaultPaths = new LinkedHashSet<String>(YamlDefaultsMerger.paths(defaultsYaml));
        Set<String> workingPaths = new LinkedHashSet<String>(YamlDefaultsMerger.paths(working));
        List<String> missing = sortedWithout(defaultPaths, workingPaths);
        List<String> unknown = sortedWithout(workingPaths, defaultPaths);
        Set<String> required = codec instanceof ConfigSchema ? ((ConfigSchema) codec).getRequiredPaths() : Collections.<String>emptySet();
        List<String> requiredMissing = sortedWithout(required, workingPaths);
        if (!requiredMissing.isEmpty()) {
            throw new IllegalStateException("Required configuration values are missing from " + file.getName() + ": "
                    + String.join(", ", requiredMissing));
        }
        if (options.getMissingValues() == MissingValuePolicy.FAIL && !missing.isEmpty()) {
            throw new IllegalStateException("Missing configuration values in " + file.getName() + ": " + String.join(", ", missing));
        }
        if (options.getUnknownValues() == UnknownValuePolicy.FAIL && !unknown.isEmpty()) {
            throw new IllegalStateException("Unknown configuration values in " + file.getName() + ": " + String.join(", ", unknown));
        }
        return new Differences(missing, unknown);
    }

    private static List<String> sortedWithout(Set<String> from, Set<String> removed) {
        Set<String> rest = new TreeSet<String>(from);
        rest.removeAll(removed);
        return new ArrayList<String>(rest);
    }

    private YamlDefaultsMerger.Result synchronize(String working, String defaultsYaml) {
        if (options.getUnknownValues() != UnknownValuePolicy.REMOVE) {
            return YamlDefaultsMerger.merge(working, defaultsYaml, options.getMissingValues(), options.getComments());
        }
        String canonical = fileStore.normalize(codec.encode(decodeAndValidate(working)));
        ConfigSteps<?> migrations = options.getMigrations();
        if (migrations != null) {
            canonical = fileStore.normalize(migrationEngine.stampDefaults(canonical, migrations));
        }
        return new YamlDefaultsMerger.Result(canonical, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    private ConfigLoadResult<T> writeChanges(String original, String synchronizedContent, T value, Differences differences,
                                             List<String> migrations, YamlDefaultsMerger.Result syncResult) throws IOException {
        File backup = options.getBackups() ? fileStore.backup(original) : null;
        fileStore.writeDecoded(synchronizedContent);
        boolean removeUnknown = options.getUnknownValues() == UnknownValuePolicy.REMOVE;
        if (!differences.missing.isEmpty()) {
            logger.info("Добавлены новые параметры в " + file.getName() + ": " + String.join(", ", differences.missing));
        }
        if (!differences.unknown.isEmpty() && removeUnknown) {
            logger.info("Удалены неизвестные параметры из " + file.getName() + ": " + String.join(", ", differences.unknown));
        }
        if (!migrations.isEmpty()) {
            logger.info("Применены миграции " + file.getName() + ": " + String.join(", ", migrations));
        }
        loadedValue = value;
        return new ConfigLoadResult<T>(value,
                options.getMissingValues() == MissingValuePolicy.ADD ? differences.missing : Collections.<String>emptyList(),
                backup,
                removeUnknown ? differences.unknown : Collections.<String>emptyList(),
                syncResult.getAddedComments(),
                migrations);
    }

    /** Reloads the file and replaces the value only after full validation. */
    @Override
    @NotNull
    public synchronized ConfigLoadResult<T> reload() {
        return load();
    }

    /** Saves the value that is currently loaded. */
    @Override
    public synchronized void save() {
        save(get());
    }

    /** Atomically saves {@code value} after decoding and semantic validation. */
    @Override
    public synchronized void save(T value) {
        if (options.getReadOnly()) {
            throw new IllegalStateException("Configuration " + file.getName() + " is read-only");
        }
        if (!skippedUnknownTypes.isEmpty()) {
            throw new IllegalStateException("Configuration " + file.getName()
                    + " has elements of an unknown type that are not in memory: "
                    + String.join("; ", skippedUnknownTypes) + ". Saving would delete them from the file");
        }
        String raw = fileStore.normalize(codec.encode(value));
        ConfigSteps<?> migrations = options.getMigrations();
        String encoded = migrations != null ? fileStore.normalize(migrationEngine.stampDefaults(raw, migrations)) : raw;
        decodeAndValidate(encoded);
        try {
            fileStore.writeDecoded(encoded);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        loadedValue = value;
    }

    /** Applies {@code updater} to the loaded value and saves the result. */
    @Override
    public synchronized T update(@NotNull UnaryOperator<T> updater) {
        T updated = updater.apply(get());
        save(updated);
        return updated;
    }

    /** Runs the validator on the loaded value without writing anything. */
    @Override
    @NotNull
    public List<ConfigProblem> validate() {
        return validator.validate(get());
    }

    /** Writes the code defaults to the file and makes them the loaded value. */
    @Override
    public synchronized T resetToDefaults() {
        save(defaults);
        return defaults;
    }

    /** Clears the in-memory value without changing the file. */
    @Override
    public synchronized void unload() {
        loadedValue = null;
    }

    private T decodeAndValidate(String content) {
        T value;
        try {
            value = codec.decode(content);
        } catch (Exception error) {
            logger.error("Не удалось прочитать " + file.getName() + ": " + error.getMessage(), error);
            throw error;
        }
        skippedUnknownTypes = codec instanceof AnnotatedYamlCodec
                ? ((AnnotatedYamlCodec<?>) codec).getSkippedUnknownTypes() : Collections.<String>emptyList();
        List<ConfigProblem> problems = validator.validate(value);
        if (!problems.isEmpty()) {
            throw new ConfigValidationException(problems);
        }
        return value;
    }
}
