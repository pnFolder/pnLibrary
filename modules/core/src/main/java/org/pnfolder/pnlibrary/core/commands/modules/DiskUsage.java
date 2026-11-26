package org.pnfolder.pnlibrary.core.commands.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Counts the bytes of the {@code plugins} folder, plugin by plugin; never fails, an unreadable file counts as zero. */
public final class DiskUsage {
    private static final Set<String> CONFIGS = new HashSet<String>(
            Arrays.asList("yml", "yaml", "json", "toml", "properties", "conf", "cfg", "hocon", "ini", "xml"));
    private static final Set<String> LIBRARY_FOLDERS = new HashSet<String>(Arrays.asList("lib", "libs", "libraries"));
    private static volatile Path pluginsFolder;

    private DiskUsage() {
    }

    /** The {@code plugins} folder of this server; the runtime sets it at start, {@code null} until then. */
    @Nullable
    public static Path getPluginsFolder() {
        return pluginsFolder;
    }

    public static void setPluginsFolder(@Nullable Path folder) {
        pluginsFolder = folder;
    }

    /** Scans {@code plugins}; {@code registered} are the jars of registered libraries, counted once each. */
    @NotNull
    public static DiskReport scan(@Nullable Path plugins, @NotNull Collection<String> registered) {
        long known = registeredSize(registered);
        if (plugins == null || !Files.isDirectory(plugins)) {
            return new DiskReport(Collections.emptyList(), known, -1, -1);
        }
        Map<String, DiskRow> rows = new LinkedHashMap<String, DiskRow>();
        List<Path> children = children(plugins);
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                rows.put(child.getFileName().toString().toLowerCase(Locale.ROOT), folderRow(child));
            }
        }
        for (Path child : children) {
            if (child.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                addJar(rows, child);
            }
        }
        List<DiskRow> sorted = new ArrayList<DiskRow>(rows.values());
        Collections.sort(sorted, new Comparator<DiskRow>() {
            @Override
            public int compare(DiskRow left, DiskRow right) {
                return Long.compare(right.getTotal(), left.getTotal());
            }
        });
        FileStore store = storeOf(plugins);
        return new DiskReport(sorted, known, space(store, true), space(store, false));
    }

    private static long registeredSize(Collection<String> registered) {
        Set<String> seen = new HashSet<String>();
        long known = 0;
        for (String path : registered) {
            File file = new File(path);
            if (file.isFile() && seen.add(file.getAbsolutePath())) {
                known += file.length();
            }
        }
        return known;
    }

    private static List<Path> children(Path plugins) {
        try (Stream<Path> stream = Files.list(plugins)) {
            return new ArrayList<Path>(java.util.Arrays.asList(stream.toArray(Path[]::new)));
        } catch (IOException | RuntimeException unreadable) {
            return Collections.emptyList();
        }
    }

    private static void addJar(Map<String, DiskRow> rows, Path jar) {
        String name = jar.getFileName().toString();
        String base = name.substring(0, name.length() - 4).replaceAll("[-_ ]v?\\d.*$", "");
        String key = base.toLowerCase(Locale.ROOT);
        long size;
        try {
            size = Files.size(jar);
        } catch (IOException unreadable) {
            size = 0L;
        }
        DiskRow row = rows.get(key);
        rows.put(key, (row == null ? new DiskRow(base, 0, 0, 0, 0, 0) : row).withJarAdded(size));
    }

    @Nullable
    private static FileStore storeOf(Path plugins) {
        try {
            return Files.getFileStore(plugins);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static long space(@Nullable FileStore store, boolean usable) {
        if (store == null) {
            return -1L;
        }
        try {
            return usable ? store.getUsableSpace() : store.getTotalSpace();
        } catch (IOException | RuntimeException unreadable) {
            return -1L;
        }
    }

    private static DiskRow folderRow(Path folder) {
        long[] sums = new long[4];
        try (Stream<Path> stream = Files.walk(folder)) {
            Iterator<Path> files = stream.iterator();
            while (files.hasNext()) {
                Path file = files.next();
                if (Files.isRegularFile(file)) {
                    count(folder, file, sums);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // A folder that cannot be walked counts what was read before it failed.
        }
        return new DiskRow(folder.getFileName().toString(), 0, sums[0], sums[1], sums[2], sums[3]);
    }

    private static void count(Path folder, Path file, long[] sums) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException unreadable) {
            size = 0L;
        }
        Path relative = folder.relativize(file);
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        String fileName = file.getFileName().toString();
        String extension = fileName.indexOf('.') < 0 ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (LIBRARY_FOLDERS.contains(first) && relative.getNameCount() > 1) {
            sums[1] += size;
        } else if (CONFIGS.contains(extension)) {
            sums[0] += size;
        } else if (extension.equals("log") || first.equals("logs") || (extension.equals("gz") && first.startsWith("log"))) {
            sums[2] += size;
        } else {
            sums[3] += size;
        }
    }

    /** {@code 1.5 МБ}, {@code 320 КБ}, {@code 12 Б}; the same text in the command and the menu. */
    @NotNull
    public static String format(long bytes) {
        if (bytes < 0) {
            return "неизвестно";
        }
        if (bytes < 1024) {
            return bytes + " Б";
        }
        if (bytes < 1024 * 1024) {
            return bytes / 1024 + " КБ";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f МБ", bytes / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.2f ГБ", bytes / 1073741824.0);
    }

    /** Registered libraries of this server and the size of the jar of each, for {@link LibraryRow}. */
    @NotNull
    public static List<String> registeredFiles(@NotNull PnLibrary library) {
        List<String> files = new ArrayList<String>();
        for (LibraryRow row : LibraryRow.all(library)) {
            if (row.getFile() != null && !files.contains(row.getFile())) {
                files.add(row.getFile());
            }
        }
        return files;
    }
}
