package org.pnfolder.pnlibrary.common.lang

/** Which failures a boundary that shields callers from broken plugins or platforms may swallow. */
object Failures {
    /**
     * Rethrows [error] when the JVM itself is failing (out of memory, stack overflow, internal error); that is not
     * ours to hide. Linkage errors such as `NoClassDefFoundError` or `NoSuchMethodError` are not fatal: they are how
     * an incompatible platform bridge or plugin shows up, and callers fall back or log instead.
     */
    @JvmStatic
    fun rethrowIfFatal(error: Throwable) {
        if (error is VirtualMachineError) throw error
    }
}

/**
 * Like [runCatching], but a failing JVM is rethrown and an interrupt is not lost: the interrupt flag is restored
 * before the failure is returned.
 */
inline fun <T> runCatchingNonFatal(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    Failures.rethrowIfFatal(error)
    if (error is InterruptedException) Thread.currentThread().interrupt()
    Result.failure(error)
}
