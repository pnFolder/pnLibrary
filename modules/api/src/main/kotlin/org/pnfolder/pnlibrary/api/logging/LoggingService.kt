package org.pnfolder.pnlibrary.api.logging


/** Creates plugin-bound loggers and console message boxes. */
interface LoggingService {

    /** Returns the logger called [name] that belongs to the plugin [owner]. */
    fun logger(owner: Any, name: String): PnLogger

    /** Starts a startup summary block titled [title] for [owner]; nothing is printed until `show()`. */
    fun box(owner: Any, title: String): MessageBox

    /** Starts a shutdown summary block titled [title] for [owner]. */
    fun shutdownBox(owner: Any, title: String): MessageBox

    /** Same as [box] with the title `name version`. */
    fun box(owner: Any, name: String, version: String): MessageBox = box(owner, "$name $version")

    /** Same as [shutdownBox] with the title `name version`. */
    fun shutdownBox(owner: Any, name: String, version: String): MessageBox =
        shutdownBox(owner, "$name $version")
}
