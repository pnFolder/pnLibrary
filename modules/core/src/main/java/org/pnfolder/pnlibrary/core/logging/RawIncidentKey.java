package org.pnfolder.pnlibrary.core.logging;

import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.Objects;

/** What a record looks like before redaction; equal keys always redact and fingerprint to the same incident. */
final class RawIncidentKey {
    private final String plugin;
    private final LogLevel level;
    private final String message;
    private final String errorClass;
    private final String errorMessage;
    private final String frameClass;
    private final String frameMethod;

    RawIncidentKey(String plugin, LogLevel level, String message, String errorClass, String errorMessage,
                   String frameClass, String frameMethod) {
        this.plugin = plugin;
        this.level = level;
        this.message = message;
        this.errorClass = errorClass;
        this.errorMessage = errorMessage;
        this.frameClass = frameClass;
        this.frameMethod = frameMethod;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RawIncidentKey)) {
            return false;
        }
        RawIncidentKey key = (RawIncidentKey) other;
        return plugin.equals(key.plugin) && level == key.level && message.equals(key.message)
            && Objects.equals(errorClass, key.errorClass) && Objects.equals(errorMessage, key.errorMessage)
            && Objects.equals(frameClass, key.frameClass) && Objects.equals(frameMethod, key.frameMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, level, message, errorClass, errorMessage, frameClass, frameMethod);
    }
}
