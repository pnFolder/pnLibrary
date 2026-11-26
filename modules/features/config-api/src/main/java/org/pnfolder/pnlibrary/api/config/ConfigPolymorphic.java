package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface or abstract class as a polymorphic configuration value.
 *
 * <p>{@link #discriminator()} names the map key selecting an implementation. With the default discriminator, a value
 * can look like {@code { type: mysql, host: localhost }}. Accepted implementations come from {@link ConfigTypes} and
 * visible dynamic registrations in {@link ConfigScope#type}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigPolymorphic {
    /** Map key containing the implementation identifier. */
    String discriminator() default "type";
}
