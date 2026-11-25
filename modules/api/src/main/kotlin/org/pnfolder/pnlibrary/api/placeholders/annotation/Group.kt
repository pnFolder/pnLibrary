package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue

/**
 * Marks a parameter whose class gathers several arguments: `fun kills(@Group query: KillsQuery)`. The class needs
 * no annotation; it has one public constructor and every parameter of it is an [ArgumentValue] marked with [Argument].
 * The arguments stand in the address one after another, in the order of the constructor.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Group
