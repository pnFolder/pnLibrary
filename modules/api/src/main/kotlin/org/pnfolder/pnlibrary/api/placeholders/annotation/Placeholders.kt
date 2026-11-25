package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.registry.Visibility

/**
 * Marks a class whose members are placeholders of one group: `@Placeholders("clan")` makes `{clan.level}`.
 * Pass an instance to `module.placeholders(instance)`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Placeholders(val value: String, val visibility: Visibility = Visibility.ALL)
