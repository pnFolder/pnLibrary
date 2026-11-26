package org.pnfolder.pnlibrary.api.config;

/** What to do with fields present in code defaults but absent from YAML. */
public enum MissingValuePolicy {
    /** Insert missing fields into YAML and persist the synchronized document. */
    ADD,

    /** Use code defaults in memory without adding the missing fields to YAML. */
    USE_DEFAULT,

    /** Reject the file when a model field is missing. */
    FAIL
}
