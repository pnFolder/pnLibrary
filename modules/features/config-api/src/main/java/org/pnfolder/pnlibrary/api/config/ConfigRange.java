package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a numeric field to remain inside the inclusive range [{@link #min()}, {@link #max()}].
 *
 * <p>The annotation is validated after decoding. Applying it to a non-numeric value produces a configuration problem
 * rather than silently accepting the field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigRange {
    /** Inclusive minimum accepted numeric value. */
    double min() default -Double.MAX_VALUE;

    /** Inclusive maximum accepted numeric value. */
    double max() default Double.MAX_VALUE;
}
