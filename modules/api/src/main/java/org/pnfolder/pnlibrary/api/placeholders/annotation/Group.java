package org.pnfolder.pnlibrary.api.placeholders.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter whose class gathers several arguments: {@code kills(@Group KillsQuery query)}. The class needs no
 * annotation; it has one public constructor and every parameter of it is an {@code ArgumentValue} marked with
 * {@link Argument}. The arguments stand in the address one after another, in the order of the constructor.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Group {
}
