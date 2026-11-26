package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field from loading, validation, and saving.
 *
 * <p>In Kotlin use {@code @field:ConfigIgnore} so the annotation is placed on the backing field inspected by the
 * codec. Static, transient, and synthetic fields are ignored independently.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigIgnore {
}
