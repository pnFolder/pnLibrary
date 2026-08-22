package ru.privatenull.pnlibrary.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Logger;

/** Writes YAML through a validated temporary file and an atomic replacement when supported. */
public final class AtomicYamlStore {

    private AtomicYamlStore() {
    }

    public static boolean save(FileConfiguration yaml, File target, Logger logger) {
        return save(yaml, target, logger, path -> { });
    }

    public static boolean save(
            FileConfiguration yaml,
            File target,
            Logger logger,
            FilePostProcessor postProcessor
    ) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(postProcessor, "postProcessor");
        Path temporary = null;
        try {
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create directory " + parent);
            }
            Path parentPath = parent == null ? target.toPath().toAbsolutePath().getParent() : parent.toPath();
            temporary = Files.createTempFile(parentPath, target.getName() + ".", ".tmp");
            yaml.save(temporary.toFile());
            postProcessor.apply(temporary);

            YamlConfiguration readback = new YamlConfiguration();
            readback.load(temporary.toFile());
            try {
                Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | InvalidConfigurationException exception) {
            logger.warning("Не удалось безопасно сохранить " + target.getName() + ": " + exception.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @FunctionalInterface
    public interface FilePostProcessor {
        void apply(Path file) throws IOException;
    }
}
