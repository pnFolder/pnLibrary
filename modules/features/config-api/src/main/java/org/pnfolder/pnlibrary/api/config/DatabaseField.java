package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says which part of a connection a field holds, so the field can have any name. Use it next to
 * {@link BackupDatabase}; fields without it are found by their usual names.
 *
 * <p>With several databases in one class, {@link #database()} names the {@link BackupDatabase#name()} the field
 * belongs to. A field without it is shared by all of them, and a field with it wins over a shared one.
 *
 * <pre>{@code
 * @DatabaseField(role = DatabaseRole.HOST)
 * private String address = "localhost";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DatabaseField {
    /** Which part of the connection the field holds. */
    DatabaseRole role();

    /** {@link BackupDatabase#name()} this field belongs to; empty means every database of the class. */
    String database() default "";
}
