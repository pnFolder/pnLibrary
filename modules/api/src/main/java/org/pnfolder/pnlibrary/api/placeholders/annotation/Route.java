package org.pnfolder.pnlibrary.api.placeholders.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the parameter that receives the {@code RouteInfo}: every part of the asked address and what each one is
 * (group, branch, argument, field, format). {@code Reply<WarBranch> war(@Route RouteInfo route)}
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Route {
}
