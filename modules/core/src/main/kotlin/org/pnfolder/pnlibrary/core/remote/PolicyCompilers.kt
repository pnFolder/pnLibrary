package org.pnfolder.pnlibrary.core.remote

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * Compiles a policy with the server's javac, or with ECJ when the server runs a JRE without a compiler. The platform
 * loader puts ECJ on the plugin classpath at start (see LibraryLoader), so it is only looked up here.
 */
internal class PolicyCompilers(
    private val systemCompiler: () -> JavaCompiler? = ToolProvider::getSystemJavaCompiler,
    private val ecjLoader: ClassLoader = PolicyCompilers::class.java.classLoader,
) {
    fun compile(file: Path, root: Path, classpath: String) {
        val system = systemCompiler()
        if (system != null) {
            run(system, javacOptions(system, classpath, root), file)
            return
        }
        run(ecj(), ecjOptions(classpath, root), file)
    }

    private fun ecj(): JavaCompiler {
        val type = try {
            Class.forName(ECJ_COMPILER, true, ecjLoader)
        } catch (missing: ClassNotFoundException) {
            throw IllegalStateException("Java compiler is unavailable: the server runs a JRE and ECJ was not loaded at start", missing)
        }
        return type.getDeclaredConstructor().newInstance() as JavaCompiler
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

    private companion object {
        const val ECJ_COMPILER = "org.eclipse.jdt.internal.compiler.tool.EclipseCompiler"
    }
}
