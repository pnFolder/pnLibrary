package org.pnfolder.pnlibrary.spi.libraries

/**
 * A library pnLibrary does not pack and downloads through Libby only when something needs it, such as a database
 * driver or the policy compiler. Nothing is loaded for features a server never uses.
 *
 * @property id group name known to the loader: `mysql`, `mongo` or `compiler`
 * @property probe a class of the library, used to tell whether it is already visible
 */
class OptionalLibrary(private val id: String, private val probe: String, private val classLoader: ClassLoader) {
    /** Whether the library can be used now, loading it first when it is missing. Never throws; false means it is unavailable. */
    fun ensure(): Boolean = present() || (loadThroughLoader() && present())

    private fun present(): Boolean = try {
        Class.forName(probe, false, classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        false
    }

    // The loader is plain Java in the same JAR and runs before Kotlin exists, so this module cannot depend on it.
    private fun loadThroughLoader(): Boolean = try {
        Class.forName("org.pnfolder.pnlibrary.loader.LibraryLoader", true, classLoader)
            .getMethod("loadOptional", String::class.java).invoke(null, id) as Boolean
    } catch (_: ReflectiveOperationException) {
        false
    }
}
