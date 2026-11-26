package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a database that pnLibrary copies before an update of this plugin and can restore when the update is
 * rolled back. Repeatable, so one class can describe several databases; their names must differ within a plugin.
 *
 * <p>Three placements are supported:
 * <ul>
 *   <li>On a field holding a connection section (a nested class): that section's fields describe the connection.</li>
 *   <li>On a scalar {@code String} field: the field is the database name, and the other connection fields of the
 *       same class are tied to it by {@link DatabaseField} with the same {@link #name()}.</li>
 *   <li>On the configuration class itself, when the connection fields sit at the top of the file.</li>
 * </ul>
 *
 * <p>Connection fields are found by their usual names ({@code host}, {@code port}, {@code database}, {@code user} or
 * {@code username}, {@code password}); mark them with {@link DatabaseField} to use any name. Mark the password with
 * {@link ConfigSecret} so reports hide it.
 *
 * <pre>{@code
 * @BackupDatabase(kind = DatabaseKind.POSTGRESQL, name = "Clans", tables = {"clans", "clan_members"})
 * private String clansDb = "clans";
 * @DatabaseField(role = DatabaseRole.HOST, database = "Clans")
 * private String clansHost = "pg.local";
 * }</pre>
 *
 * <p>Only tables (collections in MongoDB) are copied; triggers, procedures and views are not.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
@Repeatable(BackupDatabase.Container.class)
public @interface BackupDatabase {
    /** The database server type. */
    DatabaseKind kind();

    /** Short name shown by the menu and commands; also the key that ties {@link DatabaseField} and {@link BackupTable} to it. */
    String name();

    /** Tables or collections to copy; {@code *} in a name matches any text ({@code "log_*"}); empty means all of them. */
    String[] tables() default {};

    /** Container that makes {@link BackupDatabase} repeatable. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface Container {
        /** The repeated declarations. */
        BackupDatabase[] value();
    }
}
