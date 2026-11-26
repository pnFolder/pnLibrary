package org.pnfolder.pnlibrary.api.placeholders.annotation;

import org.pnfolder.pnlibrary.api.registry.Visibility;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a method or a property a placeholder, named after it in lower case. It returns a {@code Reply}. A method whose
 * reply holds a plain value is a leaf; a method that returns an object becomes a branch whose own {@code @Placeholder}
 * members continue the address.
 *
 * <p>On a branch, an {@link #onInvalid()} other than {@link Invalid#KEEP} covers every text placeholder below it that
 * sets none of its own.
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Placeholder {
    /** Description shown in {@code /pn placeholders}. */
    String value() default "";

    /** What to show when there is no value. */
    Invalid onInvalid() default Invalid.KEEP;

    /** The text for {@link Invalid#DEFAULT}. */
    String defaultText() default "";

    /** Who may use this placeholder. */
    Visibility visibility() default Visibility.ALL;

    /**
     * Leaves the method name out of the address: its {@code @Argument} slots follow the group (or the branch) right
     * away, so {@code @Placeholders("keys")} with a bare {@code keys(@Argument name)} answers {@code keys.donat}. A word
     * of a sibling placeholder is tried first. Needs at least one argument, and a class may have one such member.
     */
    boolean bare() default false;
}
