package org.pnfolder.pnlibrary.api.tasks.spec

/**
 * Platform execution context selected for a task.
 *
 * @property kind execution-context category
 * @property target platform-native entity for [Kind.ENTITY], otherwise `null`
 */
class TaskExecution private constructor(val kind: Kind, val target: Any?) {
    /** Supported execution-context categories. */
    enum class Kind {
        /** The platform's global (main) context. */
        GLOBAL,

        /** pnLibrary's asynchronous executor. */
        ASYNC,

        /** The context bound to one native entity, such as a Folia region. */
        ENTITY,
    }

    override fun toString(): String = if (target == null) kind.name else "$kind($target)"

    companion object {
        /** Selects the platform's global execution context. */
        @JvmStatic
        fun global(): TaskExecution = TaskExecution(Kind.GLOBAL, null)

        /** Selects pnLibrary's asynchronous executor. */
        @JvmStatic
        fun async(): TaskExecution = TaskExecution(Kind.ASYNC, null)

        /** Selects the execution context associated with native [target]. */
        @JvmStatic
        fun entity(target: Any): TaskExecution = TaskExecution(Kind.ENTITY, target)
    }
}
