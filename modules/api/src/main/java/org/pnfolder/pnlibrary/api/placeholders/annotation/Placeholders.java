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
    /** The group, the first word of every address. Empty for a {@link #root() root} class. */
    String value() default "";

    /**
     * A group without a word: the members stand right under the prefix of the plugin ({@code %plugin_word%}), and a
     * {@code @Placeholder(bare = true)} member with an {@code @Argument} gives {@code %plugin_<argument>%}. A word of
     * a member or a group wins over the bare member. A module may have one bare member in all its root classes.
     */
    boolean root() default false;

    /** Who may use the placeholders of the group. */
    Visibility visibility() default Visibility.ALL;
}
