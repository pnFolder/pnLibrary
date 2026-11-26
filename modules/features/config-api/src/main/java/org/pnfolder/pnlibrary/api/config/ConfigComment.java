package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Writes documentation lines above a YAML field or at the beginning of a configuration class.
 *
 * <pre>{@code
 * @ConfigComment({"Maximum connections retained by the pool.", "Range: 1..64"})
 * private int poolSize = 10;
 * }</pre>
 *
 * <p>This produces {@code # Maximum connections retained by the pool.} followed by {@code pool-size: 10} when kebab
 * naming is enabled. Each element becomes one comment line without the {@code #} prefix.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ConfigComment {
    /** Documentation lines written without YAML comment prefixes. */
    String[] value();
}
