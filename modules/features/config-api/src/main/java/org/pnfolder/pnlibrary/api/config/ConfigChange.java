package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** A file changed: it was read, written, or one of its settings was edited. */
public final class ConfigChange {
    private final ConfigId id;
    private final ConfigChangeCause cause;
    private final List<String> paths;

    /**
     * Creates a change record.
     *
     * @param id the file
     * @param cause what happened
     * @param paths YAML paths that changed, empty when the whole file is meant
     */
    public ConfigChange(@Nullable ConfigId id, @NotNull ConfigChangeCause cause, @NotNull List<String> paths) {
        this.id = id;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    /** The file. */
    @Nullable
    public ConfigId getId() {
        return id;
    }

    /** What happened. */
    @NotNull
    public ConfigChangeCause getCause() {
        return cause;
    }

    /** YAML paths that changed, empty when the whole file is meant. */
    @NotNull
    public List<String> getPaths() {
        return paths;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigChange)) {
            return false;
        }
        ConfigChange that = (ConfigChange) other;
        return Objects.equals(id, that.id) && cause == that.cause && paths.equals(that.paths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cause, paths);
    }

    @Override
    public String toString() {
        return "ConfigChange(id=" + id + ", cause=" + cause + ", paths=" + paths + ")";
    }
}
