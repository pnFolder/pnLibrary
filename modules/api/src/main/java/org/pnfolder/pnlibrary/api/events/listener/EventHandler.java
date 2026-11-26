package org.pnfolder.pnlibrary.api.events.listener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler.
 *
 * <p>The method must accept exactly one {@link org.pnfolder.pnlibrary.api.events.Event} parameter and return
 * {@code void}. It may use any visibility. Smaller numeric priorities run first.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHandler {
    /** Numeric dispatch order; any integer is valid. */
    int priority() default EventPriority.NORMAL;

    /** Skip the handler when the event is already denied ({@code Event.isAllowed()} is {@code false}). */
    boolean ignoreDenied() default false;
}
