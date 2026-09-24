package ru.privatenull.pnlibrary.remote.bukkit

import java.io.Closeable
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

internal class RemoteClassLoader private constructor(
    private val loader: ClassLoader,
    val className: String,
    private val temporaryRoot: Path? = null,
) : Closeable {
    fun load(): Class<*> = Class.forName(className, true, loader)

    override fun close() {
        (loader as? Closeable)?.close()
        temporaryRoot?.let { root ->
            if (Files.exists(root)) Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        fun compiled(root: Path, name: String, parent: ClassLoader): RemoteClassLoader =
            RemoteClassLoader(URLClassLoader(arrayOf(root.toUri().toURL()), parent), name, root)

        fun forBytes(bytes: ByteArray, name: String?, parent: ClassLoader): RemoteClassLoader {
            require(!name.isNullOrBlank()) { "class name is required for compiled policy" }
            if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                val root = Files.createTempDirectory("pnlibrary-remote-jar-")
                val jar = root.resolve("policy.jar")
                Files.write(jar, bytes)
                return RemoteClassLoader(URLClassLoader(arrayOf(jar.toUri().toURL()), parent), name, root)
            }
            require(bytes.size >= 8 && bytes[0] == 0xCA.toByte() && bytes[1] == 0xFE.toByte() && bytes[2] == 0xBA.toByte() && bytes[3] == 0xBE.toByte()) {
                "remote file is not a Java class"
            }
            val major = ((bytes[6].toInt() and 255) shl 8) or (bytes[7].toInt() and 255)
            val java = System.getProperty("java.specification.version", "8").removePrefix("1.").substringBefore('.').toInt()
            require(major <= 44 + java) { "remote class requires newer Java" }
            val loader = object : ClassLoader(parent) {
                override fun findClass(requested: String): Class<*> {
                    if (requested != name) throw ClassNotFoundException(requested)
                    return defineClass(requested, bytes, 0, bytes.size)
                }
            }
            return RemoteClassLoader(loader, name)
        }
    }
}
