package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a polymorphic base type accept objects without a {@code type}, converted by {@link #value()} first.
 *
 * <p>The rewriter class must expose a no-argument constructor or Kotlin {@code object} instance.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigNodeForm {
    /** Rewriter applied to every object read for the base type. */
    Class<? extends ConfigNodeRewriter> value();
}
