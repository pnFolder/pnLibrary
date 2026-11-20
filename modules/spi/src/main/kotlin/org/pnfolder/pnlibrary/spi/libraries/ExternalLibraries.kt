package org.pnfolder.pnlibrary.spi.libraries

/**
 * Loads a library a module asked for through the platform's Libby loader, which this module cannot depend on: the loader
 * is plain Java in the same JAR, so it is called by name, like [OptionalLibrary] does.
 */
object ExternalLibraries {
    /**
     * Downloads and loads one library and returns the class loader that holds it, or `null` when it joined pnLibrary's own
     * loader (`isolated` false).
     *
     * @throws IllegalStateException with the reason when it cannot be loaded
     */
    fun load(
        classLoader: ClassLoader,
        owner: String,
        group: String,
        artifact: String,
        version: String,
        classifier: String?,
        sha256: String?,
        url: String?,
        repositories: List<String>,
        isolated: Boolean,
        plugin: Any? = null,
    ): ClassLoader? = try {
        Class.forName("org.pnfolder.pnlibrary.loader.LibraryLoader", true, classLoader)
            .getMethod(
                "loadExternal", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java,
                String::class.java, String::class.java, Array<String>::class.java, Boolean::class.javaPrimitiveType, Any::class.java,
            )
            .invoke(null, owner, group, artifact, version, classifier, sha256, url, repositories.toTypedArray(), isolated, plugin) as ClassLoader?
    } catch (failure: java.lang.reflect.InvocationTargetException) {
        throw IllegalStateException(failure.targetException.message ?: "Library cannot be loaded", failure.targetException)
    } catch (_: ReflectiveOperationException) {
        throw IllegalStateException("The library loader is not available on this server")
    }

    /** The jar file of a library this loader has loaded, as a path, or `null` when it is not known (never loaded, or no loader). */
    fun fileOf(classLoader: ClassLoader, coordinates: String): String? = try {
        Class.forName("org.pnfolder.pnlibrary.loader.LibraryLoader", true, classLoader)
            .getMethod("fileOf", String::class.java).invoke(null, coordinates) as String?
    } catch (_: ReflectiveOperationException) {
        null
    }
}
