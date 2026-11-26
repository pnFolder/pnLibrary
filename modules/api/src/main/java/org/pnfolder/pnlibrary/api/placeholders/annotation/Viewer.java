package org.pnfolder.pnlibrary.api.placeholders.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the parameter that receives the {@code ViewerInfo}: who asks, in which language, and through which way. The
 * library fills it; it is not a part of the address. {@code Reply<Integer> level(@Viewer ViewerInfo viewer)}
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Viewer {
}
