package org.pnfolder.pnlibrary.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows a field in the in-game settings menu ({@code /pn gui}) and lets {@code /pn configs edit} change it.
 *
 * <p>Only marked fields are shown; secrets ({@link ConfigSecret}) never are. Supported types: boolean, whole and
 * decimal numbers, text and enums. A {@link ConfigRange} on the field limits a number, {@link ConfigStep} sets the
 * click step, {@link ConfigDuration} marks a length of time and {@link ConfigHint} describes a text.
 *
 * <pre>{@code
 * @ConfigEditable(title = "Приветствие", description = "Текст, который видит игрок при входе.")
 * private String greeting = "Добро пожаловать!";
 *
 * @ConfigEditable(title = "Размер пула", description = "Сколько соединений держать открытыми.", apply = ConfigApply.RESTART)
 * @ConfigRange(min = 1.0, max = 64.0)
 * private int poolSize = 10;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigEditable {
    /** Short name for a player; the YAML key when empty. */
    String title() default "";

    /** What the setting does, shown next to it. */
    String description() default "";

    /** When a change starts to work. */
    ConfigApply apply() default ConfigApply.LIVE;
}
