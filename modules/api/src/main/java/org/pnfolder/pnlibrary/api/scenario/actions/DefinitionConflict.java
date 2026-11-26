package org.pnfolder.pnlibrary.api.scenario.actions;

/**
 * What to do when the same module already defined that name, see {@link ActionService#define}. A name taken by
 * another module is no conflict: both stay, the nearest answers and the library reports the clash.
 */
public enum DefinitionConflict {
    /** Refuse with an error; the default, so that a clash is never silent. */
    FAIL,

    /** Keep the existing definition and define nothing; the returned handle does nothing. */
    KEEP,

    /** Replace the existing definition of this module; a definition of another module is never touched. */
    REPLACE
}
