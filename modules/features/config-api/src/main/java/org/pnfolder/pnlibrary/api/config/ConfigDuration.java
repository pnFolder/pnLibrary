package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a whole number as a length of time kept in {@link #unit()}. The menu shows it as {@code 1 ч 30 мин}, steps by
 * seconds, minutes or hours, and accepts typed values such as {@code 90m}, {@code 1h30m} or {@code 45с}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigDuration {
    /** The unit the number is kept in. */
    TimeUnit unit();
}
