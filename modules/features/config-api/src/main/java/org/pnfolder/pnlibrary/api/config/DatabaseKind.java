package org.pnfolder.pnlibrary.api.config;

/** A kind of database pnLibrary can copy before an update and put back on a rollback. */
public enum DatabaseKind {
    /** MySQL and MariaDB. */
    MYSQL,

    /** MongoDB; documents are copied with their indexes, without a snapshot in time. */
    MONGODB,

    /** PostgreSQL; tables of the current schema are copied with indexes, constraints and sequences. */
    POSTGRESQL
}
