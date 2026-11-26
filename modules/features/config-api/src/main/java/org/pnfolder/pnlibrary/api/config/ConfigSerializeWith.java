package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Uses a {@link ConfigSerializer} implementation for this field or every occurrence of this class.
 *
 * <p>The serializer class must expose a no-argument constructor or Kotlin {@code object} instance. A field annotation
 * takes precedence over a class annotation and a scope-registered serializer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ConfigSerializeWith {
    /** Serializer implementation instantiated for the annotated value. */
    Class<? extends ConfigSerializer<?>> value();
}
