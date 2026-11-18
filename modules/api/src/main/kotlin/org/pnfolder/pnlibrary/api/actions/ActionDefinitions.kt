package org.pnfolder.pnlibrary.api.actions

/** What to do when a definition name is already taken, see [ActionService.define]. */
enum class DefinitionConflict {
    /** Refuse with an error; the default, so that a clash is never silent. */
    FAIL,

    /** Keep the existing definition and define nothing; the returned handle does nothing. */
    KEEP,

    /** Replace the existing definition of the same plugin or module; a definition of another owner is never replaced. */
    REPLACE,
}

/** How far a definition reaches, see [ActionService.define]. */
enum class DefinitionScope {
    /** Only scenarios of the module that defined it. */
    MODULE,

    /** Every module of every plugin; close the handle when your plugin is disabled. */
    LIBRARY,
}
