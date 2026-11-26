package org.pnfolder.pnlibrary.api.placeholders.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies the format {@link #value()} when the text names none. On a method it covers that placeholder, on a class
 * every placeholder under it whose value is of {@link #type()} (any type when it is left out).
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UseFormat {
    /** The name of the format. */
    String value();

    /** The type of value the format covers; {@code Object.class} means any. */
    Class<?> type() default Object.class;
}
