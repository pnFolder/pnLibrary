package org.pnfolder.pnlibrary.api.observability.observation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable event stored in the shared observability journal. */
public final class Observation {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 1_024;
    private static final int MAX_DATA_ENTRIES = 32;
    private static final int MAX_DATA_KEY_LENGTH = 128;
    private static final int MAX_DATA_VALUE_LENGTH = 2_048;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4_096;

    private final String id;
    private final long timestamp;
    private final String plugin;
    private final String source;
    private final String message;
    private final ObservationLevel level;
    private final Map<String, String> data;
    private final String errorType;
    private final String errorMessage;

    /**
     * Creates an observation as it is stored.
     *
     * @param id globally unique observation identifier
     * @param timestamp creation time expressed as Unix epoch milliseconds
     * @param plugin logical plugin name, when known
     * @param source component or operation, when known
     * @param message concise human-readable description
     * @param level severity and retention priority
     * @param data bounded searchable metadata
     * @param errorType fully qualified throwable type, when present
     * @param errorMessage throwable message, when present
     */
    public Observation(@NotNull String id, long timestamp, @Nullable String plugin, @Nullable String source,
                       @NotNull String message, @NotNull ObservationLevel level, @NotNull Map<String, String> data,
                       @Nullable String errorType, @Nullable String errorMessage) {
        this.id = Objects.requireNonNull(id);
        this.timestamp = timestamp;
        this.plugin = plugin;
        this.source = source;
        this.message = Objects.requireNonNull(message);
        this.level = Objects.requireNonNull(level);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /** Creates an observation from {@code request} stamped with the current time. */
    @NotNull
    public static Observation from(@NotNull ObservationRequest request) {
        return from(request, System.currentTimeMillis());
    }

    /** Creates an observation from {@code request} stamped with {@code timestamp} (epoch milliseconds). */
    @NotNull
    public static Observation from(@NotNull ObservationRequest request, long timestamp) {
        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : request.getData().entrySet()) {
            if (data.size() >= MAX_DATA_ENTRIES) {
                break;
            }
            data.put(take(entry.getKey(), MAX_DATA_KEY_LENGTH), take(entry.getValue(), MAX_DATA_VALUE_LENGTH));
        }
        Throwable error = request.getError();
        return new Observation(
                UUID.randomUUID().toString(),
                timestamp,
                request.getPlugin() == null ? null : take(request.getPlugin(), MAX_ID_LENGTH),
                request.getSource() == null ? null : take(request.getSource(), MAX_ID_LENGTH),
                take(request.getMessage(), MAX_MESSAGE_LENGTH),
                request.getLevel(),
                data,
                error == null ? null : error.getClass().getName(),
                error == null || error.getMessage() == null ? null : take(error.getMessage(), MAX_ERROR_MESSAGE_LENGTH));
    }

    private static String take(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length);
    }

    /** Globally unique observation identifier. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Creation time expressed as Unix epoch milliseconds. */
    public long getTimestamp() {
        return timestamp;
    }

    /** The logical plugin name, when known. */
    @Nullable
    public String getPlugin() {
        return plugin;
    }

    /** The component or operation, when known. */
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

    /** The fully qualified throwable type, when present. */
    @Nullable
    public String getErrorType() {
        return errorType;
    }

    /** The throwable message, when present. */
    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Observation)) {
            return false;
        }
        Observation that = (Observation) other;
        return timestamp == that.timestamp && id.equals(that.id) && Objects.equals(plugin, that.plugin)
                && Objects.equals(source, that.source) && message.equals(that.message) && level == that.level
                && data.equals(that.data) && Objects.equals(errorType, that.errorType)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, timestamp, plugin, source, message, level, data, errorType, errorMessage);
    }

    @NotNull
    @Override
    public String toString() {
        return "Observation(id=" + id + ", timestamp=" + timestamp + ", plugin=" + plugin + ", source=" + source
                + ", message=" + message + ", level=" + level + ")";
    }
}
