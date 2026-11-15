package org.pnfolder.pnlibrary.spi.platform

/**
 * Marks the log records the library writes itself, so a log observer does not report them back.
 *
 * The mark belongs to the calling thread and nests: a record is own while any [run] on its thread is active.
 */
class OwnLogScope {
    private val active = ThreadLocal.withInitial { false }

    /** Whether the current thread is inside [run]. */
    val isActive: Boolean get() = active.get()

    /** Runs [block] while records produced by this thread are treated as the library's own. */
    fun <T> run(block: () -> T): T {
        val previous = active.get()
        active.set(true)
        try {
            return block()
        } finally {
            active.set(previous)
        }
    }
}
