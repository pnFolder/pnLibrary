package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue

/**
 * Creates a format named [value]: `{clan.level|rank}`. The first parameter of the method is the value, the
 * following [ArgumentValue] parameters read what comes after the colon (`|pad0:4`). Pass the holder to `module.formats(holder)`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Format(val value: String)
