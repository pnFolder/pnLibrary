package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Uses the code-defined field value when YAML contains an invalid value and emits a warning.
 *
 * <p>Recovery is local to the annotated field. Other invalid fields still fail loading, and the fallback does not
 * hide semantic validation errors reported after object construction.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigDefaultOnInvalid {
}
