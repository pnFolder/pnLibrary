package org.pnfolder.pnlibrary.api.placeholders.annotation;

import org.pnfolder.pnlibrary.api.registry.Visibility;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose members are placeholders of one group: {@code @Placeholders("clan")} makes {@code {clan.level}}.
 * Pass an instance to {@code module.placeholders(instance)}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Placeholders {
    /** The group, the first word of every address. */
    String value();

    /** Who may use the placeholders of the group. */
    Visibility visibility() default Visibility.ALL;
}
