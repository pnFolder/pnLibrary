package ru.privatenull.pnlibrary.core.remote

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import ru.privatenull.pnlibrary.api.remote.RemotePolicy
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import javax.tools.ToolProvider

/** Shared source loader used by every platform adapter. */
internal object RemotePolicyEngine {
    private const val MAX_BYTES = 1024L * 1024L
    private val packagePattern = Regex("\\bpackage\\s+([A-Za-z_\$][\\w\$]*(?:\\.[A-Za-z_\$][\\w\$]*)*)\\s*;?")
    private val classPattern = Regex("\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_\$][\\w\$]*)")

    fun check(url: String, context: RemotePolicyContext): RemotePolicyResult {
        val uri = URI(url)
        val extension = when {
            uri.path.endsWith(".java", true) -> "java"
            uri.path.endsWith(".kt", true) -> "kt"
            else -> throw IllegalArgumentException("Remote policy source must be a .java or .kt file")
        }
        val source = when {
            uri.scheme.equals("https", true) -> download(uri)
            uri.scheme.equals("file", true) -> readLocal(uri)
            else -> throw IllegalArgumentException("Remote policy requires an HTTPS or file URL")
        }
        return checkSource(source, context, extension)
    }

    private fun readLocal(uri: URI): ByteArray {
        val path = Paths.get(uri).toAbsolutePath().normalize()
        require(Files.isRegularFile(path)) { "Remote policy file does not exist: $path" }
        require(Files.size(path) <= MAX_BYTES) { "Remote policy exceeds $MAX_BYTES bytes" }
        return Files.readAllBytes(path)
    }

    internal fun checkSource(
        source: ByteArray,
        context: RemotePolicyContext,
        extension: String = "java",
    ): RemotePolicyResult {
        val text = source.toString(StandardCharsets.UTF_8)
        val simpleName = classPattern.find(text)?.groupValues?.get(1)
            ?: error("Remote policy does not declare a class")
        val packageName = packagePattern.find(text)?.groupValues?.get(1)
        val name = if (packageName == null) simpleName else "$packageName.$simpleName"
        val root = Files.createTempDirectory("pnlibrary-policy-")
        try {
            val file = root.resolve(name.replace('.', '/') + ".$extension")
            Files.createDirectories(file.parent)
            Files.write(file, source)
            val classpath = listOf(System.getProperty("java.class.path", ""), location(RemotePolicy::class.java), location(RemotePolicyContext::class.java))
                .filter(String::isNotBlank).joinToString(File.pathSeparator)
            if (extension.equals("kt", true)) compileKotlin(file, root, classpath)
            else compileJava(file, root, classpath)
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

    private fun compileJava(file: Path, root: Path, classpath: String) {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("Java compiler is unavailable; run the server with a JDK")
            val compatibility = if (compiler.isSupportedOption("--release") >= 0) {
                arrayOf("--release", "8")
            } else {
                arrayOf("-source", "8", "-target", "8")
            }
            val arguments = compatibility + arrayOf("-classpath", classpath, "-d", root.toString(), file.toString())
            val exit = compiler.run(null, null, null, *arguments)
            require(exit == 0) { "Remote policy compilation failed" }
    }

    private fun compileKotlin(file: Path, root: Path, classpath: String) {
        val diagnostics = ByteArrayOutputStream()
        val exit = PrintStream(diagnostics, true, "UTF-8").use { output ->
            K2JVMCompiler().exec(
                output,
                "-d", root.toString(),
                "-classpath", classpath,
                "-jvm-target", "1.8",
                "-no-stdlib",
                "-no-reflect",
                file.toString(),
            )
        }
        require(exit == ExitCode.OK) {
            diagnostics.toString("UTF-8").trim().ifBlank { "Remote Kotlin policy compilation failed: $exit" }
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
