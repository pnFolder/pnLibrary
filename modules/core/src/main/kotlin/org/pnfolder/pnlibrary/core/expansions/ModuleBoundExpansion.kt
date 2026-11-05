package org.pnfolder.pnlibrary.core.expansions

import org.pnfolder.pnlibrary.api.expansions.Expansion

internal interface ModuleBoundExpansion<V : Any> : Expansion {
    /** Creates the view for one module, once per module; whatever it acquires for the module goes through [ModuleBinding.own]. */
    fun bind(module: ModuleBinding): V
}

/** What a view owns on behalf of its module: closed, newest first, when the module closes. */
internal interface ModuleBinding {
    /** Acquires a resource for the module. Throws [IllegalStateException] without acquiring when the module is closed. */
    fun <R : AutoCloseable> own(acquire: () -> R): R
}
