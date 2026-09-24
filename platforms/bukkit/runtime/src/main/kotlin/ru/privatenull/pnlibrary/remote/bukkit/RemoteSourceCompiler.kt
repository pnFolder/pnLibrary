package ru.privatenull.pnlibrary.remote.bukkit

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.tools.ToolProvider

internal object RemoteSourceCompiler {
    private val packagePattern = Regex("\\bpackage\\s+([A-Za-z_\$][\\w\$]*(?:\\.[A-Za-z_\$][\\w\$]*)*)\\s*;")
    private val classPattern = Regex("\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_\$][\\w\$]*)")

    fun compile(source: ByteArray, parent: ClassLoader): RemoteClassLoader {
        val text = source.toString(StandardCharsets.UTF_8)
        val simpleName = classPattern.find(text)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("remote .java file does not contain a class")
        val packageName = packagePattern.find(text)?.groupValues?.get(1)
        val name = if (packageName == null) simpleName else "$packageName.$simpleName"
        val root = Files.createTempDirectory("pnlibrary-remote-source-")
        try {
            val file = root.resolve(name.replace('.', '/') + ".java")
            Files.createDirectories(file.parent)
            Files.write(file, source)
            val compiler = ToolProvider.getSystemJavaCompiler()
                ?: throw IllegalStateException("JavaCompiler is unavailable; run the server with a JDK")
            val classpath = listOf(System.getProperty("java.class.path", ""), location(RemoteCheck::class.java), location(JavaPlugin::class.java))
                .filter { it.isNotBlank() }.joinToString(File.pathSeparator)
            val result = compiler.run(null, null, null, "-source", "8", "-target", "8", "-classpath", classpath,
                "-d", root.toString(), file.toString())
            require(result == 0) { "remote Java policy compilation failed" }
            return RemoteClassLoader.compiled(root, name, parent)
        } catch (error: Throwable) {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            throw error
        }
    }

    private fun location(type: Class<*>): String = try {
        File(type.protectionDomain.codeSource.location.toURI()).path
    } catch (_: Exception) { "" }
}
