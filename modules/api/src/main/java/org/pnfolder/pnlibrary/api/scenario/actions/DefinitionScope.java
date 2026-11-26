package org.pnfolder.pnlibrary.api.scenario.actions;

/**
 * How far a definition reaches, see {@link ActionService#define}. Whoever asks gets the nearest one of a name: the
 * own module, the own plugin, the others. A name may start with the owner and {@code @}: {@code ~@give-kit} (own
 * plugin), {@code ~~@give-kit} (own module), {@code shop/main@give-kit}, {@code shop@give-kit}.
 */
public enum DefinitionScope {
    /** Only scenarios of the module that defined it. */
    MODULE,

    /** Scenarios of every module of the same plugin. */
    PLUGIN,

    /** Every module of every plugin; it goes away when the module that defined it is closed. */
    LIBRARY
}
