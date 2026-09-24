package ru.privatenull.pnlibrary.remote.bukkit;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RemoteSourceCompiler {
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");
    private static final Pattern TYPE = Pattern.compile("\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)");

    static RemoteClassLoader compile(byte[] source, ClassLoader parent) throws Exception {
        String text = new String(source, StandardCharsets.UTF_8);
        Matcher type = TYPE.matcher(text);
        if (!type.find()) throw new IllegalArgumentException("remote .java file does not contain a class");
        Matcher pkg = PACKAGE.matcher(text);
        String className = (pkg.find() ? pkg.group(1) + "." : "") + type.group(1);
        Path root = Files.createTempDirectory("pnlibrary-remote-source-");
        Path file = root.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent()); Files.write(file, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JavaCompiler is unavailable; run the server with a JDK");
        String classpath = System.getProperty("java.class.path", "")
                + java.io.File.pathSeparator + location(RemoteCheck.class)
                + java.io.File.pathSeparator + location(org.bukkit.plugin.java.JavaPlugin.class);
        int result = compiler.run(null, null, null, "-source", "8", "-target", "8",
                "-classpath", classpath, "-d", root.toString(), file.toString());
        if (result != 0) throw new IllegalArgumentException("remote Java policy compilation failed");
        URLClassLoader loader = new URLClassLoader(new URL[] { root.toUri().toURL() }, parent);
        return RemoteClassLoader.fromCompiled(loader, root, className);
    }

    private static String location(Class<?> type) {
        try { return new java.io.File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath(); }
        catch (Exception ignored) { return ""; }
    }
}
