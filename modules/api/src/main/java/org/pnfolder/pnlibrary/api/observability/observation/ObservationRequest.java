package org.pnfolder.pnlibrary.api.observability.observation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete input for recording one observation.
 *
 * <p>Files are copied when the request is recorded. The journal stores generated attachment identifiers rather than
 * the original absolute paths.
 */
public final class ObservationRequest {
    private final String plugin;
    private final String source;
    private final String message;
    private final ObservationLevel level;
    private final Map<String, String> data;
    private final List<Path> files;
    private final Throwable error;

    /** An {@link ObservationLevel#INFO} request with only a message. */
    public ObservationRequest(@NotNull String message) {
        this(null, null, message, ObservationLevel.INFO, Collections.<String, String>emptyMap(),
                Collections.<Path>emptyList(), null);
    }

    /** A request about a plugin and component without data, files and failure. */
    public ObservationRequest(@Nullable String plugin, @Nullable String source, @NotNull String message) {
        this(plugin, source, message, ObservationLevel.INFO);
    }

    /** A request with a severity but without data, files and failure. */
    public ObservationRequest(@Nullable String plugin, @Nullable String source, @NotNull String message,
                              @NotNull ObservationLevel level) {
        this(plugin, source, message, level, Collections.<String, String>emptyMap());
    }

    /** A request with metadata but without files and failure. */
    public ObservationRequest(@Nullable String plugin, @Nullable String source, @NotNull String message,
                              @NotNull ObservationLevel level, @NotNull Map<String, String> data) {
        this(plugin, source, message, level, data, Collections.<Path>emptyList(), null);
    }

    /** A request with metadata and files but without a failure. */
    public ObservationRequest(@Nullable String plugin, @Nullable String source, @NotNull String message,
                              @NotNull ObservationLevel level, @NotNull Map<String, String> data, @NotNull List<Path> files) {
        this(plugin, source, message, level, data, files, null);
    }

    /**
     * Creates a request.
     *
     * @param plugin optional logical plugin name
     * @param source optional component or operation that produced the event
     * @param message concise human-readable description
     * @param level severity and retention priority
     * @param data bounded searchable metadata
     * @param files local files to attach to this observation
     * @param error optional failure captured as type and message
     */
    public ObservationRequest(@Nullable String plugin, @Nullable String source, @NotNull String message,
                              @NotNull ObservationLevel level, @NotNull Map<String, String> data,
                              @NotNull List<Path> files, @Nullable Throwable error) {
        this.plugin = plugin;
        this.source = source;
        this.message = Objects.requireNonNull(message);
        this.level = Objects.requireNonNull(level);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
        this.error = error;
    }

    /** The logical plugin name, or {@code null}. */
    @Nullable
    public String getPlugin() {
        return plugin;
    }

    /** The component or operation that produced the event, or {@code null}. */
    @Nullable
    public String getSource() {
        return source;
    }

    /** The concise human-readable description. */
    @NotNull
    public String getMessage() {
        return message;
    }

    /** The severity and retention priority. */
    @NotNull
    public ObservationLevel getLevel() {
        return level;
    }

    /** The bounded searchable metadata. */
    @NotNull
    public Map<String, String> getData() {
        return data;
    }

    /** The local files to attach. */
    @NotNull
    public List<Path> getFiles() {
        return files;
    }

    /** The failure captured as type and message, or {@code null}. */
    @Nullable
    public Throwable getError() {
        return error;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ObservationRequest)) {
            return false;
        }
        ObservationRequest that = (ObservationRequest) other;
        return Objects.equals(plugin, that.plugin) && Objects.equals(source, that.source) && message.equals(that.message)
                && level == that.level && data.equals(that.data) && files.equals(that.files) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, source, message, level, data, files, error);
    }

    @NotNull
    @Override
    public String toString() {
        return "ObservationRequest(plugin=" + plugin + ", source=" + source + ", message=" + message + ", level=" + level + ")";
    }
}
