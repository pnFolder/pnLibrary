package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read-only description of one registered configuration file. It never carries values from the file,
 * so {@code @ConfigSecret} fields cannot leak through it.
 */
public final class ConfigInfo {
    private final ConfigId id;
    private final Class<?> modelType;
    private final Path file;
    private final boolean exists;
    private final boolean loaded;
    private final Instant loadedAt;
    private final boolean readOnly;
    private final List<String> skippedUnknownTypes;
    private final List<ConfigProblem> problems;
    private final ConfigApply pendingApply;

    /** Creates a description of a file without settings waiting for an apply. */
    public ConfigInfo(@NotNull ConfigId id, @NotNull Class<?> modelType, @NotNull Path file, boolean exists,
                      boolean loaded, @Nullable Instant loadedAt, boolean readOnly,
                      @NotNull List<String> skippedUnknownTypes, @NotNull List<ConfigProblem> problems) {
        this(id, modelType, file, exists, loaded, loadedAt, readOnly, skippedUnknownTypes, problems, null);
    }

    /**
     * Creates a description of a file.
     *
     * @param id identifier of the file
     * @param modelType class the file is read into
     * @param file full path on disk
     * @param exists whether the file exists now
     * @param loaded whether a value is currently held in memory
     * @param loadedAt time of the last successful load or reload
     * @param readOnly whether the library only reads the file and never writes it
     * @param skippedUnknownTypes list elements skipped at the last load because their {@code type} is not known
     * @param problems validation problems of the loaded value, empty when not loaded
     * @param pendingApply the strongest requirement of settings changed since the last read that do not work yet
     */
    public ConfigInfo(@NotNull ConfigId id, @NotNull Class<?> modelType, @NotNull Path file, boolean exists,
                      boolean loaded, @Nullable Instant loadedAt, boolean readOnly,
                      @NotNull List<String> skippedUnknownTypes, @NotNull List<ConfigProblem> problems,
                      @Nullable ConfigApply pendingApply) {
        this.id = id;
        this.modelType = modelType;
        this.file = file;
        this.exists = exists;
        this.loaded = loaded;
        this.loadedAt = loadedAt;
        this.readOnly = readOnly;
        this.skippedUnknownTypes = skippedUnknownTypes;
        this.problems = problems;
        this.pendingApply = pendingApply;
    }

    /** Identifier of the file. */
    @NotNull
    public ConfigId getId() {
        return id;
    }

    /** Class the file is read into. */
    @NotNull
    public Class<?> getModelType() {
        return modelType;
    }

    /** Full path on disk. */
    @NotNull
    public Path getFile() {
        return file;
    }

    /** Whether the file exists now. */
    public boolean getExists() {
        return exists;
    }

    /** Whether a value is currently held in memory. */
    public boolean isLoaded() {
        return loaded;
    }

    /** Time of the last successful load or reload. */
    @Nullable
    public Instant getLoadedAt() {
        return loadedAt;
    }

    /** Whether the library only reads the file and never writes it. */
    public boolean getReadOnly() {
        return readOnly;
    }

    /** List elements skipped at the last load because their {@code type} is not known. */
    @NotNull
    public List<String> getSkippedUnknownTypes() {
        return skippedUnknownTypes;
    }

    /** Validation problems of the loaded value, empty when not loaded. */
    @NotNull
    public List<ConfigProblem> getProblems() {
        return problems;
    }

    /** The strongest requirement of settings changed since the last read that do not work yet, or {@code null}. */
    @Nullable
    public ConfigApply getPendingApply() {
        return pendingApply;
    }

    /** The address of this exact file, ready to be passed to {@code into(...)}. */
    @NotNull
    public ConfigAddress getAddress() {
        return id.getAddress();
    }

    /** The address of the module that owns this file. */
    @NotNull
    public ConfigAddress getModuleAddress() {
        return id.getModuleAddress();
    }

    /** Plugin of the file. */
    @NotNull
    public PluginId getPlugin() {
        return id.getPlugin();
    }

    /** Module of the file. */
    @NotNull
    public ModuleId getModule() {
        return id.getModule();
    }

    /** Path inside the root. */
    @NotNull
    public String getPath() {
        return id.getPath();
    }

    /** Root of the path. */
    @NotNull
    public ConfigRoot getRoot() {
        return id.getRoot();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigInfo)) {
            return false;
        }
        ConfigInfo that = (ConfigInfo) other;
        return exists == that.exists && loaded == that.loaded && readOnly == that.readOnly && id.equals(that.id)
                && modelType.equals(that.modelType) && file.equals(that.file) && Objects.equals(loadedAt, that.loadedAt)
                && skippedUnknownTypes.equals(that.skippedUnknownTypes) && problems.equals(that.problems)
                && pendingApply == that.pendingApply;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, modelType, file, exists, loaded, loadedAt, readOnly, skippedUnknownTypes, problems,
                pendingApply);
    }

    @Override
    public String toString() {
        return "ConfigInfo(id=" + id + ", modelType=" + modelType + ", file=" + file + ", exists=" + exists
                + ", isLoaded=" + loaded + ", loadedAt=" + loadedAt + ", readOnly=" + readOnly
                + ", skippedUnknownTypes=" + skippedUnknownTypes + ", problems=" + problems
                + ", pendingApply=" + pendingApply + ")";
    }
}
