package org.pnfolder.pnlibrary.api.placeholders.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Creates a format named {@link #value()}: {@code {clan.level|rank}}. The first parameter of the method is the value,
 * the following {@code ArgumentValue} parameters read what comes after the colon ({@code |pad0:4}). Pass the holder to
 * {@code module.formats(holder)}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Format {
    /** The name written after the bar. */
    String value();
}
