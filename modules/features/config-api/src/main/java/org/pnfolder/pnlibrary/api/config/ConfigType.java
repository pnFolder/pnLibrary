package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Associates one polymorphic implementation with its discriminator identifier. */
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigType {
    /** Concrete class instantiated for this choice. */
    Class<?> type();

    /** Primary case-insensitive discriminator value written during serialization. */
    String name();

    /** Additional case-insensitive values accepted only during deserialization. */
    String[] aliases() default {};

    /** Tie-break priority when multiple declarations accept the same identifier. */
    int priority() default 0;
}
