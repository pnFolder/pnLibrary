package org.pnfolder.pnlibrary.api.config;

/** What a field of a database connection section holds. */
public enum DatabaseRole {
    /** Server host name or address. */
    HOST,

    /** Server port. */
    PORT,

    /** Database (schema) name. */
    DATABASE,

    /** Login of the account. */
    USER,

    /** Password of the account; also mark the field with {@link ConfigSecret}. */
    PASSWORD,

    /** A full connection string such as {@code mongodb://user:pass@host/db}; when present it replaces host, port, user and password. */
    URI
}
