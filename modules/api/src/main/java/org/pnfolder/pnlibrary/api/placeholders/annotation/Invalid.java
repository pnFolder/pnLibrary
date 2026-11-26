package org.pnfolder.pnlibrary.api.placeholders.annotation;

/** What a placeholder shows when its method has no value (returns {@code null}). */
public enum Invalid {
    /** Leave the address in the text as it is. This is the default. */
    KEEP,
    /** Show {@link Placeholder#defaultText()}. Only for text placeholders. */
    DEFAULT,
    /** Show nothing. Only for text placeholders. */
    EMPTY
}
