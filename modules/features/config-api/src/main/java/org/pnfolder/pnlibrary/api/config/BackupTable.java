package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a text field that holds the name of a table (or collection) to copy, for plugins whose table names are set in
 * the configuration. Several fields may carry it; together with {@link BackupDatabase#tables()} both lists are
 * joined.
 *
 * <pre>{@code
 * @BackupTable
 * private String table = "clans";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BackupTable {
    /** {@link BackupDatabase#name()} this table belongs to; empty means every database of the class. */
    String database() default "";
}
