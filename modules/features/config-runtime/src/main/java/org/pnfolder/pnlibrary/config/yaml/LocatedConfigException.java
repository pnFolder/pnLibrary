package org.pnfolder.pnlibrary.config.yaml;

/** A conversion failure already reported at its own location; outer entries pass it on unchanged. */
final class LocatedConfigException extends IllegalArgumentException {
    static final String INVALID_VALUE = "Invalid configuration value at";

    private static final long serialVersionUID = 1L;

    LocatedConfigException(String location, String reason, Throwable cause) {
        super(INVALID_VALUE + " " + location + ": " + reason, cause);
    }
}
