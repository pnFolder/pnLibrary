package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

/** One semantic configuration problem. */
public final class ConfigProblem {
    private final String path;
    private final String message;

    /**
     * Creates a problem.
     *
     * @param path full dot-separated YAML path, such as {@code database.pool-size}
     * @param message human-readable explanation without the path prefix
     */
    public ConfigProblem(@NotNull String path, @NotNull String message) {
        this.path = path;
        this.message = message;
    }

    /** Full dot-separated YAML path. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Human-readable explanation without the path prefix. */
    @NotNull
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigProblem && path.equals(((ConfigProblem) other).path)
                && message.equals(((ConfigProblem) other).message);
    }

    @Override
    public int hashCode() {
        return 31 * path.hashCode() + message.hashCode();
    }

    @Override
    public String toString() {
        return "ConfigProblem(path=" + path + ", message=" + message + ")";
    }
}
