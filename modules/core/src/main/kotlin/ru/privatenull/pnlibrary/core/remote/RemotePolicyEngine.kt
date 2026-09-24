package ru.privatenull.pnlibrary.core.remote

import ru.privatenull.pnlibrary.api.remote.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import javax.tools.ToolProvider

/** Shared source loader used by every platform adapter. */
internal object RemotePolicyEngine {
    private const val MAX_BYTES = 1024L * 1024L
    private val packagePattern = Regex("\\bpackage\\s+([A-Za-z_\$][\\w\$]*(?:\\.[A-Za-z_\$][\\w\$]*)*)\\s*;")
    private val classPattern = Regex("\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_\$][\\w\$]*)")

    fun check(url: String, context: RemotePolicyContext): RemotePolicyResult {
        val uri = URI(url)
        require(uri.scheme.equals("https", true)) { "Remote policy requires HTTPS" }
        require(uri.path.endsWith(".java", true)) { "Remote policy source must be a .java file" }
        val source = download(uri)
        return checkSource(source, context)
    }

    internal fun checkSource(source: ByteArray, context: RemotePolicyContext): RemotePolicyResult {
        val text = source.toString(StandardCharsets.UTF_8)
        val simpleName = classPattern.find(text)?.groupValues?.get(1)
            ?: error("Remote policy does not declare a class")
        val packageName = packagePattern.find(text)?.groupValues?.get(1)
        val name = if (packageName == null) simpleName else "$packageName.$simpleName"
        val root = Files.createTempDirectory("pnlibrary-policy-")
        try {
            val file = root.resolve(name.replace('.', '/') + ".java")
            Files.createDirectories(file.parent)
            Files.write(file, source)
            val compiler = ToolProvider.getSystemJavaCompiler()
                ?: error("Java compiler is unavailable; run the server with a JDK")
            val classpath = listOf(System.getProperty("java.class.path", ""), location(RemotePolicy::class.java), location(RemotePolicyContext::class.java))
                .filter(String::isNotBlank).joinToString(File.pathSeparator)
            val exit = compiler.run(null, null, null, "-source", "8", "-target", "8", "-classpath", classpath,
                "-d", root.toString(), file.toString())
            require(exit == 0) { "Remote policy compilation failed" }
            URLClassLoader(arrayOf(root.toUri().toURL()), RemotePolicy::class.java.classLoader).use { loader ->
                val type = Class.forName(name, true, loader)
                require(RemotePolicy::class.java.isAssignableFrom(type)) { "Remote class must implement RemotePolicy" }
                val policy = type.getDeclaredConstructor().newInstance() as RemotePolicy
                return policy.check(context)
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun download(uri: URI): ByteArray {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 30_000
        try {
            require(connection.responseCode in 200..299) { "Remote policy HTTP ${connection.responseCode}" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= MAX_BYTES) { "Remote policy exceeds $MAX_BYTES bytes" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun location(type: Class<*>): String = try {
        File(type.protectionDomain.codeSource.location.toURI()).path
    } catch (_: Exception) { "" }
}
