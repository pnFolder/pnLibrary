package ru.privatenull.pnlibrary.remote.bukkit;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;

final class RemoteClassLoader implements Closeable {
    private final ClassLoader loader;
    private final java.nio.file.Path temporaryRoot;

    private RemoteClassLoader(ClassLoader loader) { this(loader, null); }
    private RemoteClassLoader(ClassLoader loader, java.nio.file.Path temporaryRoot) { this.loader = loader; this.temporaryRoot = temporaryRoot; }
    static RemoteClassLoader fromCompiled(ClassLoader loader, java.nio.file.Path root, String className) { return new RemoteClassLoader(new NamedLoader(loader, className), root); }

    static RemoteClassLoader forBytes(byte[] bytes, String className, ClassLoader parent) throws Exception {
        if (bytes.length < 8 || bytes[0] != 'P' || bytes[1] != 'K') {
            verifyBytecodeLevel(bytes);
            return new RemoteClassLoader(new SingleClassLoader(bytes, className, parent));
        }
        java.nio.file.Path jar = java.nio.file.Files.createTempFile("pnlibrary-remote-class-", ".jar");
        java.nio.file.Files.write(jar, bytes);
        return new RemoteClassLoader(new URLClassLoader(new URL[] { jar.toUri().toURL() }, parent) {
            @Override public void close() throws IOException { super.close(); java.nio.file.Files.deleteIfExists(jar); }
        });
    }

    Class<?> load(String name) throws ClassNotFoundException { return Class.forName(name, true, loader); }
    @Override public void close() throws IOException { if (loader instanceof Closeable) ((Closeable) loader).close(); if (temporaryRoot != null) delete(temporaryRoot); }
    private static void delete(java.nio.file.Path root) throws IOException { if (Files.exists(root)) Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }

    private static void verifyBytecodeLevel(byte[] bytes) {
        if (bytes.length < 8 || bytes[0] != (byte) 0xCA || bytes[1] != (byte) 0xFE || bytes[2] != (byte) 0xBA || bytes[3] != (byte) 0xBE) {
            throw new IllegalArgumentException("remote file is neither a .class file nor a JAR");
        }
        int major = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
        String specification = System.getProperty("java.specification.version", "8");
        int java = specification.startsWith("1.") ? Integer.parseInt(specification.substring(2)) : Integer.parseInt(specification.split("\\.")[0]);
        int supported = 44 + java;
        if (major > supported) throw new IllegalArgumentException("remote class requires Java bytecode " + major + ", server supports " + supported + " (Java " + java + ")");
    }

    private static final class SingleClassLoader extends ClassLoader {
        private final byte[] bytes; private final String name;
        SingleClassLoader(byte[] bytes, String name, ClassLoader parent) { super(parent); this.bytes = bytes; this.name = name; }
        @Override protected Class<?> findClass(String requested) throws ClassNotFoundException {
            if (!name.equals(requested)) throw new ClassNotFoundException(requested);
            return defineClass(requested, bytes, 0, bytes.length);
        }
    }
    private static final class NamedLoader extends ClassLoader {
        private final ClassLoader delegate; private final String name;
        NamedLoader(ClassLoader delegate, String name) { super(delegate.getParent()); this.delegate = delegate; this.name = name; }
        @Override protected Class<?> findClass(String requested) throws ClassNotFoundException { if (!name.equals(requested)) throw new ClassNotFoundException(requested); return delegate.loadClass(requested); }
    }
}
