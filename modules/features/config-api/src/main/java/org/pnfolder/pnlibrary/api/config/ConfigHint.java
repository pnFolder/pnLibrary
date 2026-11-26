package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Tells the person who edits a text what it may hold. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigHint {
    /** How the text is written, such as {@code мир;x;y;z}. */
    String format() default "";

    /** Sample values shown in the menu. */
    String[] examples() default {};

    /** The known values; with {@link #strict()} only they are accepted and the menu offers them as a list. */
    String[] values() default {};

    /** Whether anything outside {@link #values()} is refused. */
    boolean strict() default false;
}
