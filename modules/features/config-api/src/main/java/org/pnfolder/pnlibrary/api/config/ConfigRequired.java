package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fails loading when this key is physically absent from an existing YAML file.
 *
 * <p>Unlike {@link MissingValuePolicy#ADD}, this is an explicit schema requirement and is checked before missing
 * defaults are merged. A present key whose value is invalid fails through normal decoding or validation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigRequired {
}
