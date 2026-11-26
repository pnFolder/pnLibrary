package org.pnfolder.pnlibrary.api.plugin.control;

/** Where a module is in its life: running, switched off, or stopped by an error of its own hook. */
public enum ModuleState {
    /** The module's {@code onEnable} ran and it is working. */
    ENABLED,

    /** The module was switched off; its commands and tasks are released and it can be switched on again. */
    DISABLED,

    /** A hook threw: the module is not working and {@link ModuleControl#getLastError()} says why. Enabling it tries again. */
    FAILED
}
