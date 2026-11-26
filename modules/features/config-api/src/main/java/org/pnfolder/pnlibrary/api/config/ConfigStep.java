package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How far one click changes a number in the settings menu, and the unit shown next to it.
 *
 * <p>The menu also offers ten, a hundred and a thousand steps and lets the viewer type the number.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigStep {
    /** The smallest change of one click. */
    double step() default 1.0;

    /** What the number counts, such as {@code блоков} or {@code %}. */
    String unit() default "";
}
