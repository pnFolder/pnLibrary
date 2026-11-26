package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the YAML key without renaming the Java or Kotlin field.
 *
 * <p>For example, {@code @ConfigKey("api-token") String token = ""} reads and writes {@code api-token}. This explicit
 * name takes precedence over {@link ConfigOptions#getNaming()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigKey {
    /** Exact YAML key used for reading and writing the field. */
    String value();
}
