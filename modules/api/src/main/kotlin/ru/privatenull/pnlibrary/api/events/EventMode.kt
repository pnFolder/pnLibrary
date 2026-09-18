package ru.privatenull.pnlibrary.api.events

/** Execution context selected for an event dispatch. */
enum class EventMode {
    /** Dispatch through the platform's main or global execution context. */
    SYNC,

    /** Dispatch through the library's background executor. */
    ASYNC,
}
