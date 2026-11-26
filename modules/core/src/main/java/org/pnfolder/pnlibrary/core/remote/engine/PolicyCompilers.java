package org.pnfolder.pnlibrary.core.remote.engine;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.spi.libraries.OptionalLibrary;

/**
 * Compiles a policy with the server's javac, or with ECJ when the server runs a JRE without a compiler. The first time
 * one is needed, the loader downloads ECJ through Libby (see LibraryLoader); a server with javac downloads nothing.
 */
public final class PolicyCompilers {
    private static final String ECJ_COMPILER = "org.eclipse.jdt.internal.compiler.tool.EclipseCompiler";

    private final Supplier<JavaCompiler> systemCompiler;
    private final ClassLoader ecjLoader;

    public PolicyCompilers() {
        this(ToolProvider::getSystemJavaCompiler, PolicyCompilers.class.getClassLoader());
    }

    public PolicyCompilers(@NotNull Supplier<JavaCompiler> systemCompiler, @NotNull ClassLoader ecjLoader) {
        this.systemCompiler = Objects.requireNonNull(systemCompiler, "systemCompiler");
        this.ecjLoader = Objects.requireNonNull(ecjLoader, "ecjLoader");
    }

    public void compile(@NotNull Path file, @NotNull Path root, @NotNull String classpath) throws Exception {
        JavaCompiler system = systemCompiler.get();
        if (system != null) {
            run(system, javacOptions(system, classpath, root), file);
            return;
        }
        run(ecj(), ecjOptions(classpath, root), file);
    }

    private JavaCompiler ecj() throws ReflectiveOperationException {
        if (!new OptionalLibrary("compiler", ECJ_COMPILER, ecjLoader).ensure()) {
            throw new IllegalStateException("Java compiler is unavailable: the server runs a JRE and ECJ could not be downloaded");
        }
        Class<?> type = Class.forName(ECJ_COMPILER, true, ecjLoader);
        return (JavaCompiler) type.getDeclaredConstructor().newInstance();
    }

    // -Xlint:-options hides javac's "source value 8 is obsolete" note; its output goes to the error, not the red console.
    private static List<String> javacOptions(JavaCompiler compiler, String classpath, Path root) {
        List<String> options = new ArrayList<>();
        if (compiler.isSupportedOption("--release") >= 0) {
            options.addAll(Arrays.asList("--release", "8"));
        } else {
            options.addAll(Arrays.asList("-source", "8", "-target", "8"));
        }
        options.addAll(Arrays.asList("-Xlint:-options", "-classpath", classpath, "-d", root.toString()));
        return options;
    }

    // ECJ cannot use --release on a JRE (it needs the JDK's API tables), so the language level is set directly.
    private static List<String> ecjOptions(String classpath, Path root) {
        return Arrays.asList("-source", "8", "-target", "8", "-nowarn", "-proc:none", "-classpath", classpath, "-d",
            root.toString());
    }

    // getTask, not run(): ECJ's run() ends the whole JVM with System.exit.
    private static void run(JavaCompiler compiler, List<String> options, Path file) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean success;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            success = compiler.getTask(null, files, diagnostics, options, null,
                files.getJavaFileObjects(file.toFile())).call();
        }
        if (!success) {
            throw new IllegalArgumentException("Remote policy compilation failed: " + errors(diagnostics));
        }
    }

    private static String errors(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append("line ").append(diagnostic.getLineNumber()).append(": ").append(diagnostic.getMessage(null));
        }
        return text.length() > 500 ? text.substring(0, 500) : text.toString();
    }
}
