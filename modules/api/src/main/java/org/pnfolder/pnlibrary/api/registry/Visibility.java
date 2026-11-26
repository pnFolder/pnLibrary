package org.pnfolder.pnlibrary.api.registry;

/**
 * Who may use a placeholder, a format or a converter. The one that registered it always can.
 *
 * <p>When two things of the same name can be reached, the nearest wins: the own module, then the own plugin, then
 * the others, then what the library brings.
 */
public enum Visibility {
    /** Only the module that registered it. */
    MODULE,

    /** Every module of the same plugin. */
    PLUGIN,

    /** Every plugin and module, and PlaceholderAPI. This is the default. */
    ALL
}
