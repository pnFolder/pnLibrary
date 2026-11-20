package org.pnfolder.pnlibrary.api.plugin.control

/** Where a module is in its life: running, switched off, or stopped by an error of its own hook. */
enum class ModuleState {
    /** The module's `onEnable` ran and it is working. */
    ENABLED,

    /** The module was switched off; its commands and tasks are released and it can be switched on again. */
    DISABLED,

    /** A hook threw: the module is not working and [ModuleControl.lastError] says why. Enabling it tries again. */
    FAILED,
}
