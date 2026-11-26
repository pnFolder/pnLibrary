package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a polymorphic base type be written as a single line of text, read by {@link #value()}.
 *
 * <p>The parser class must expose a no-argument constructor or Kotlin {@code object} instance.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigTextForm {
    /** Parser applied when the YAML value is a string. */
    Class<? extends ConfigTextParser<?>> value();
}
