package org.pnfolder.pnlibrary.api.tasks.spec

import org.pnfolder.pnlibrary.api.tasks.run.TaskContext

/** Action invoked by a scheduled task. */
fun interface TaskAction {
    /** Performs one invocation using its live [context]. */
    fun run(context: TaskContext)
}
