package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field whose value must never appear in a diagnostic report, such as a password, a token or a webhook.
 *
 * <p>It applies to the file this class is registered for, and to a nested section when put on the field holding it.
 * The value is hidden in every report with no extra setting in the library.
 *
 * <pre>{@code
 * @ConfigSecret
 * private String password = "";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigSecret {
}
