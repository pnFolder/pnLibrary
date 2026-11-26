package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls generated field order; lower values are written first.
 *
 * <p>Fields with equal order retain reflection discovery order, so assign explicit distinct values when a stable
 * public file layout matters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigOrder {
    /** Ascending sort position used while rendering fields. */
    int value();
}
