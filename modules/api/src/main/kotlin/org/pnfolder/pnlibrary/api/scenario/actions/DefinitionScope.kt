package org.pnfolder.pnlibrary.api.scenario.actions

/** How far a definition reaches, see [ActionService.define]. */
enum class DefinitionScope {
    /** Only scenarios of the module that defined it. */
    MODULE,

    /** Every module of every plugin; close the handle when your plugin is disabled. */
    LIBRARY,
}
