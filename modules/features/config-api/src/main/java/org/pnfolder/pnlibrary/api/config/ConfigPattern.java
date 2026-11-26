package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the complete text value to match the Java regular expression in {@link #value()}.
 *
 * <p>For example, {@code @ConfigPattern("[a-z][a-z0-9_-]{2,31}")} validates an entire identifier, not merely a
 * substring. Invalid regex syntax is reported while the model is inspected.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigPattern {
    /** Java regular expression matched against the complete string. */
    String value();
}
