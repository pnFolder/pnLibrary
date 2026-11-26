package org.pnfolder.pnlibrary.core.remote.engine;

import java.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.remote.RemotePolicy;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;

/** Fetches a policy source, compiles it and asks it for a decision. */
public final class RemotePolicyEngine {
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final Pattern PACKAGE = Pattern.compile(
        "\\bpackage\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;?");
    private static final Pattern CLASS = Pattern.compile(
        "\\b(?:public\\s+)?(?:final\\s+|abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)");

    private final PolicyCompilers compilers;

    public RemotePolicyEngine(@NotNull PolicyCompilers compilers) {
        this.compilers = Objects.requireNonNull(compilers, "compilers");
    }

    public @NotNull RemotePolicyResult check(@NotNull String url, @NotNull RemotePolicyContext context) throws Exception {
        URI uri = new URI(url);
        if (!uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
            throw new IllegalArgumentException("Remote policy source must be a .java file");
        }
        byte[] source;
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            source = download(uri);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            source = readLocal(uri);
        } else {
            throw new IllegalArgumentException("Remote policy requires an HTTPS or file URL");
        }
        return checkSource(source, context);
    }

    private static byte[] readLocal(URI uri) throws Exception {
        Path path = Paths.get(uri).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Remote policy file does not exist: " + path);
        }
        if (Files.size(path) > MAX_BYTES) {
            throw new IllegalArgumentException("Remote policy exceeds " + MAX_BYTES + " bytes");
        }
        return Files.readAllBytes(path);
    }

    public @NotNull RemotePolicyResult checkSource(@NotNull byte[] source, @NotNull RemotePolicyContext context) throws Exception {
        String text = LegacyPolicyImports.rewrite(new String(source, StandardCharsets.UTF_8));
        Matcher declared = CLASS.matcher(text);
        if (!declared.find()) {
            throw new IllegalStateException("Remote policy does not declare a class");
        }
        Matcher packaged = PACKAGE.matcher(text);
        String name = packaged.find() ? packaged.group(1) + "." + declared.group(1) : declared.group(1);
        Path root = Files.createTempDirectory("pnlibrary-policy-");
        try {
            Path file = root.resolve(name.replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            compilers.compile(file, root, classpath());
            return run(root, name, context);
        } finally {
            deleteTree(root);
        }
    }

    private static RemotePolicyResult run(Path root, String name, RemotePolicyContext context) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[] {root.toUri().toURL()},
            RemotePolicy.class.getClassLoader())) {
            Class<?> type = Class.forName(name, true, loader);
            if (!RemotePolicy.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Remote class must implement RemotePolicy");
            }
            RemotePolicy policy = (RemotePolicy) type.getDeclaredConstructor().newInstance();
            return policy.check(context);
        }
    }

    private static String classpath() {
        List<String> parts = new ArrayList<>();
        for (String part : new String[] {System.getProperty("java.class.path", ""), location(RemotePolicy.class),
            location(RemotePolicyContext.class)}) {
            if (!part.trim().isEmpty()) {
                parts.add(part);
            }
        }
        return String.join(File.pathSeparator, parts);
    }

    private static void deleteTree(Path root) throws Exception {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Collections.reverseOrder()).collect(Collectors.toList());
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static byte[] download(URI uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(30_000);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code > 299) {
                throw new IllegalArgumentException("Remote policy HTTP " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                return output.toByteArray();
            }
            if ((long) output.size() + count > MAX_BYTES) {
                throw new IllegalArgumentException("Remote policy exceeds " + MAX_BYTES + " bytes");
            }
            output.write(buffer, 0, count);
        }
    }

    // Classes without a file code source (e.g. bootstrap or in-memory) simply have no location to match.
    private static String location(Class<?> type) {
        try {
            return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception noLocation) {
            return "";
        }
    }
}
