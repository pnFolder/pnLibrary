package org.pnfolder.pnlibrary.api.placeholders.annotation;

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter of type {@code ArgumentValue}: the part of the address it reads. Every such parameter needs it;
 * the options are all optional.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument {
    /** Allowed numbers for an integer argument, such as {@code "1..100"}. */
    String range() default "";

    /** Allowed words for a text argument. */
    String[] choices() default {};

    /** The name of the path part, for lists and error messages. */
    String name() default "";

    /**
     * The class that reads the text, for a type that has no default or needs another reading; see
     * {@link ArgumentConverter}. {@code ArgumentConverter.class} itself means "none".
     */
    @SuppressWarnings("rawtypes")
    Class<? extends ArgumentConverter> converter() default ArgumentConverter.class;
}
