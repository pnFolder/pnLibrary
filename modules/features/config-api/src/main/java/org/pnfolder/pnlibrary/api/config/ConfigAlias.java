package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares previous YAML spellings accepted for an enum constant.
 *
 * <pre>{@code
 * enum StorageMode {
 *     @ConfigAlias({"MYSQL", "MARIADB"})
 *     JDBC
 * }
 * }</pre>
 *
 * <p>Both legacy values deserialize as {@code JDBC}; serialization always writes {@code JDBC}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigAlias {
    /** Legacy enum spellings accepted during deserialization. */
    String[] value();
}
