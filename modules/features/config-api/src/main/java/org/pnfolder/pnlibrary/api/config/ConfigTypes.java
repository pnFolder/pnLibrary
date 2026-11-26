package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares implementations accepted by a {@link ConfigPolymorphic} type.
 *
 * <p>Place it on the base type for global declarations or on a field to extend choices only at that location. Every
 * {@link ConfigType#type()} must implement the annotated base type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ConfigTypes {
    /** Implementation declarations available at the annotation site. */
    ConfigType[] value();
}
