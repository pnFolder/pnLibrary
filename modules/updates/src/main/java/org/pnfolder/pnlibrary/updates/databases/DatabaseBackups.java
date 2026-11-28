package org.pnfolder.pnlibrary.updates.databases;

import java.util.Objects;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.DatabaseKind;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.updates.databases.mongo.MongoEngine;
import org.pnfolder.pnlibrary.updates.databases.mysql.MysqlEngine;
import org.pnfolder.pnlibrary.updates.databases.postgres.PostgresEngine;
import org.pnfolder.pnlibrary.updates.history.backup.BackupStorage;
import org.pnfolder.pnlibrary.updates.history.backup.PrivateFiles;

/**
 * Copies the databases plugins marked with {@code @BackupDatabase} before an update and puts them back on request.
 * Copies live in {@code <folder>/<plan>/<plugin>/<name>.db.gz}; before a restore the current state is copied too, so a
 * restore can itself be undone.
 */
public final class DatabaseBackups {
    /** Ending of every copy file. */
    private static final String SUFFIX = ".db.gz";
    /** Folder under a plan that holds the state replaced by a restore. */
    private static final String BEFORE_RESTORE = "before-restore";

    private final Path folder;
    private final DatabaseSources sources;
    private final Map<DatabaseKind, DatabaseEngine> engines;

    public DatabaseBackups(@NotNull Path folder, @NotNull DatabaseSources sources) {
        this(folder, sources, defaultEngines());
    }

    public DatabaseBackups(@NotNull Path folder, @NotNull DatabaseSources sources,
                           @NotNull Map<DatabaseKind, DatabaseEngine> engines) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.engines = engines;
    }

    /** Copies every marked database of {@code plugins}. A failure stops the update: it would run without its safety copy. */
    public void copyBefore(@NotNull UUID plan, @NotNull Collection<String> plugins) throws IOException {
        for (String plugin : plugins) {
            for (DatabaseSource source : sources.of(plugin)) {
                Path target = file(plan, plugin, source.getName());
                try {
                    PrivateFiles.directories(target.getParent());
                    dump(source, target);
                } catch (Exception error) {
                    BackupStorage.delete(folder.resolve(plan.toString()));
                    throw new IllegalStateException("Не удалось сохранить базу «" + source.getName() + "» плагина " + plugin
                            + ": " + error.getMessage(), error);
                }
            }
        }
    }

    /** The databases saved for {@code plan}, for the lists of backups. */
    @NotNull
    public List<UpdateBackupItem> items(@NotNull UUID plan) throws IOException {
        Path root = folder.resolve(plan.toString());
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Path> copies = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root, 2)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (path.getFileName().toString().endsWith(SUFFIX) && !path.getParent().equals(root)) {
                    copies.add(path);
                }
            }
        }
        List<UpdateBackupItem> items = new ArrayList<>();
        for (Path path : copies) {
            items.add(new UpdateBackupItem(ProductId.of(path.getParent().getFileName().toString()), BackupKind.DATABASE,
                    nameOf(path), Files.size(path)));
        }
        return items;
    }

    /** Replaces the tables of the database with the saved ones. The state it replaces is kept first. */
    public void restore(@NotNull UUID plan, @NotNull String plugin, @NotNull String name) throws Exception {
        Path saved = file(plan, plugin, name);
        if (!Files.isRegularFile(saved)) {
            throw new IllegalArgumentException("Для набора " + plan + " нет сохранённой базы «" + name + "» плагина " + plugin);
        }
        DatabaseSource source = find(plugin, name);
        Path before = folder.resolve(plan.toString()).resolve(BEFORE_RESTORE)
                .resolve(String.valueOf(Instant.now().toEpochMilli())).resolve(plugin).resolve(saved.getFileName());
        PrivateFiles.directories(before.getParent());
        dump(source, before);
        DatabaseConnection connection = DatabaseConnection.read(source);
        engineOf(source).restore(connection, saved);
    }

    private DatabaseSource find(String plugin, String name) {
        for (DatabaseSource source : sources.of(plugin)) {
            if (source.getName().equals(name)) {
                return source;
            }
        }
        throw new IllegalStateException("Плагин " + plugin + " сейчас не описывает базу «" + name
                + "»: она не отмечена @BackupDatabase или плагин не загружен");
    }

    private void dump(DatabaseSource source, Path target) throws Exception {
        DatabaseConnection connection = DatabaseConnection.read(source);
        engineOf(source).dump(connection, connection.getTables(), target);
    }

    private DatabaseEngine engineOf(DatabaseSource source) {
        DatabaseEngine engine = engines.get(source.getKind());
        if (engine == null) {
            throw new IllegalStateException("Базы " + source.getKind() + " пока не поддерживаются");
        }
        return engine;
    }

    private Path file(UUID plan, String plugin, String name) {
        return folder.resolve(plan.toString()).resolve(plugin).resolve(encode(name) + SUFFIX);
    }

    private static String nameOf(Path path) {
        String file = path.getFileName().toString();
        try {
            return URLDecoder.decode(file.substring(0, file.length() - SUFFIX.length()), "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<DatabaseKind, DatabaseEngine> defaultEngines() {
        Map<DatabaseKind, DatabaseEngine> engines = new EnumMap<>(DatabaseKind.class);
        engines.put(DatabaseKind.MYSQL, new MysqlEngine());
        engines.put(DatabaseKind.POSTGRESQL, new PostgresEngine());
        engines.put(DatabaseKind.MONGODB, new MongoEngine());
        return engines;
    }
}
