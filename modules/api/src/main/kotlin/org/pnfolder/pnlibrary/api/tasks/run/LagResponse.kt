package org.pnfolder.pnlibrary.api.tasks.run


/** What to do with a run that started late. */
enum class LagResponse {
    /** Run it anyway, late. */
    RUN,

    /** Skip this run; a repeating task goes on with the next one. */
    SKIP,

    /** Skip this run and cancel the task for good: it is not worth running after such a delay. */
    CANCEL,
}
