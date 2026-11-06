package org.pnfolder.pnlibrary.core.remote

import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/** Compiles a policy with the server's javac, or with ECJ when the server runs a JRE without a compiler. */
internal class PolicyCompilers(
    private val ecj: EcjJar,
    private val systemCompiler: () -> JavaCompiler? = ToolProvider::getSystemJavaCompiler,
) {
    fun compile(file: Path, root: Path, classpath: String) {
        val system = systemCompiler()
        if (system != null) {
            run(system, javacOptions(system, classpath, root), file)
            return
        }
        val jar = try {
            ecj.file()
        } catch (failure: Exception) {
            throw IllegalStateException("Java compiler is unavailable and ECJ could not be downloaded", failure)
        }
        // The parent is the platform loader, so ECJ never sees (or clashes with) other plugins' classes.
        val parent = ClassLoader.getSystemClassLoader().parent
        URLClassLoader(arrayOf(jar.toUri().toURL()), parent).use { loader ->
            val type = Class.forName("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler", true, loader)
            val compiler = type.getDeclaredConstructor().newInstance() as JavaCompiler
            run(compiler, ecjOptions(classpath, root), file)
        }
    }

    // -Xlint:-options hides javac's "source value 8 is obsolete" note; its output goes to the error, not the red console.
    private fun javacOptions(compiler: JavaCompiler, classpath: String, root: Path): List<String> {
        val level = if (compiler.isSupportedOption("--release") >= 0) {
            listOf("--release", "8")
        } else {
            listOf("-source", "8", "-target", "8")
        }
        return level + listOf("-Xlint:-options", "-classpath", classpath, "-d", root.toString())
    }

    // ECJ cannot use --release on a JRE (it needs the JDK's API tables), so the language level is set directly.
    private fun ecjOptions(classpath: String, root: Path): List<String> =
        listOf("-source", "8", "-target", "8", "-nowarn", "-proc:none", "-classpath", classpath, "-d", root.toString())

    // getTask, not run(): ECJ's run() ends the whole JVM with System.exit.
    private fun run(compiler: JavaCompiler, options: List<String>, file: Path) {
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { files ->
            compiler.getTask(null, files, diagnostics, options, null, files.getJavaFileObjects(file.toFile())).call()
        }
        require(success) { "Remote policy compilation failed: ${errors(diagnostics)}" }
    }

    private fun errors(diagnostics: DiagnosticCollector<JavaFileObject>): String =
        diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR }
            .joinToString("; ") { "line ${it.lineNumber}: ${it.getMessage(null)}" }.take(500)

    companion object {
        /** Compilers that keep a downloaded ECJ in the `compilers` folder of the library data. */
        fun storedIn(dataFolder: Path): PolicyCompilers =
            PolicyCompilers(EcjJar(dataFolder.resolve("compilers"), EcjBuild.forJava(JavaRuntime.feature)))
    }
}
