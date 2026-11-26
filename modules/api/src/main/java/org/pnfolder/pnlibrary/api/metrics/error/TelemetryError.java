package org.pnfolder.pnlibrary.api.metrics.error;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral, normalized error event suitable for local or remote reporting. */
public final class TelemetryError {
    private final String type;
    private final String message;
    private final List<String> stackTrace;
    private final boolean handled;
    private final String operation;
    private final Map<String, Object> attributes;

    /** An error without an operation and attributes. */
    public TelemetryError(@NotNull String type, @Nullable String message, @NotNull List<String> stackTrace, boolean handled) {
        this(type, message, stackTrace, handled, null, Collections.emptyMap());
    }

    /**
     * Creates the event.
     *
     * @param type fully qualified exception type or another stable error identifier
     * @param message human-readable error message, when one is available
     * @param stackTrace rendered stack frames ordered from the failure site outward
     * @param handled whether application code caught and handled the failure
     * @param operation optional logical operation that was executing when the failure occurred
     * @param attributes additional structured context associated with this occurrence
     */
    public TelemetryError(@NotNull String type, @Nullable String message, @NotNull List<String> stackTrace, boolean handled,
                          @Nullable String operation, @NotNull Map<String, Object> attributes) {
        this.type = Objects.requireNonNull(type);
        this.message = message;
        this.stackTrace = Collections.unmodifiableList(new ArrayList<>(stackTrace));
        this.handled = handled;
        this.operation = operation;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** The exception type or another stable error identifier. */
    @NotNull
    public String getType() {
        return type;
    }

    /** The error message, when one is available. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /** Rendered stack frames ordered from the failure site outward. */
    @NotNull
    public List<String> getStackTrace() {
        return stackTrace;
    }

    /** Whether application code caught and handled the failure. */
    public boolean isHandled() {
        return handled;
    }

    /** The logical operation that was executing, or {@code null}. */
    @Nullable
    public String getOperation() {
        return operation;
    }

    /** Additional structured context associated with this occurrence. */
    @NotNull
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TelemetryError)) {
            return false;
        }
        TelemetryError that = (TelemetryError) other;
        return handled == that.handled && type.equals(that.type) && Objects.equals(message, that.message)
                && stackTrace.equals(that.stackTrace) && Objects.equals(operation, that.operation)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, message, stackTrace, handled, operation, attributes);
    }

    @NotNull
    @Override
    public String toString() {
        return "TelemetryError(type=" + type + ", message=" + message + ", handled=" + handled + ", operation=" + operation + ")";
    }
}
