package org.pnfolder.pnlibrary.api.config;

/** What to do when an existing field has no code-defined comment. */
public enum CommentPolicy {
    /** Leave the file's existing comments unchanged. */
    PRESERVE,

    /** Add missing {@link ConfigComment} text while retaining existing comments. */
    ADD_MISSING
}
