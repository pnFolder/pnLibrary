package org.pnfolder.pnlibrary.api.scenario.actions

/** What to do when a definition name is already taken, see [ActionService.define]. */
enum class DefinitionConflict {
    /** Refuse with an error; the default, so that a clash is never silent. */
    FAIL,

    /** Keep the existing definition and define nothing; the returned handle does nothing. */
    KEEP,

    /** Replace the existing definition of the same plugin or module; a definition of another owner is never replaced. */
    REPLACE,
}
