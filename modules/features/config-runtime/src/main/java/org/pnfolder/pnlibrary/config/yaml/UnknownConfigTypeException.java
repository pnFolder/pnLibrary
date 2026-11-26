package org.pnfolder.pnlibrary.config.yaml;

/** A {@code type} word that no visible declaration accepts. A list can skip such an element instead of failing. */
public final class UnknownConfigTypeException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    UnknownConfigTypeException(String message) {
        super(message);
    }
}
