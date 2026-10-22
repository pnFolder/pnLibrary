package org.pnfolder.pnlibrary.policy.kotlin;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/** Optional compiler bridge. No server API or core implementation is needed by this extension. */
public final class KotlinPolicyCompiler {
    private KotlinPolicyCompiler() { }

    public static void compile(Path file, Path outputDirectory, String classpath) throws Exception {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        ExitCode exit;
        try (PrintStream output = new PrintStream(diagnostics, true, "UTF-8")) {
            exit = new K2JVMCompiler().exec(output, "-d", outputDirectory.toString(),
                    "-classpath", classpath, "-jvm-target", "1.8", "-no-stdlib", "-no-reflect", file.toString());
        }
        if (exit != ExitCode.OK) throw new IllegalArgumentException(
                "Remote Kotlin policy compilation failed: " + diagnostics.toString("UTF-8"));
    }
}
