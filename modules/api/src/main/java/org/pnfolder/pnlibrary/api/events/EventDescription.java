package org.pnfolder.pnlibrary.api.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional explanation of an {@link Event} class. Menus and {@code /pn bus} show it next to the event, so an
 * administrator learns what the event is for without reading the plugin's code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventDescription {
    /** What happens when the event is called, in one sentence. */
    String purpose();

    /** How a plugin is expected to use it, for example which results a listener may set; may stay empty. */
    String usage() default "";
}
