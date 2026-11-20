package org.pnfolder.pnlibrary.core.cleanup

/** The innermost cause of this failure, or the failure itself when it has none. */
internal fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

/** The message of [rootCause], or its class name when it has no message. */
internal fun Throwable.rootMessage(): String = rootCause().let { it.message ?: it.javaClass.simpleName }
