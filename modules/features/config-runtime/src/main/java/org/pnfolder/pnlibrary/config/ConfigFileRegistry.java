package org.pnfolder.pnlibrary.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigId;

/** Every file registered through any scope, by identifier. */
final class ConfigFileRegistry {
    private final Map<ConfigId, OwnedConfig<?>> files = new LinkedHashMap<ConfigId, OwnedConfig<?>>();

    /** Adds {@code owned}; a second registration of one identifier or a second writer of one file is refused. */
    void add(@NotNull OwnedConfig<?> owned) {
        synchronized (files) {
            if (files.containsKey(owned.getId())) {
                throw new IllegalStateException("Configuration " + owned.getId() + " is already registered");
            }
            if (!owned.isReadOnly()) {
                for (OwnedConfig<?> other : files.values()) {
                    if (!other.isReadOnly() && other.getFile().equals(owned.getFile())) {
                        throw new IllegalStateException("File " + owned.getFile() + " is already managed by " + other.getId()
                                + "; register it read-only to only read it");
                    }
                }
            }
            files.put(owned.getId(), owned);
        }
    }

    /** Removes the file registered under {@code id}, whatever it is. */
    void remove(@NotNull ConfigId id) {
        synchronized (files) {
            files.remove(id);
        }
    }

    /** Removes {@code owned} only when it is still the one registered under its identifier. */
    void remove(@NotNull OwnedConfig<?> owned) {
        synchronized (files) {
            if (files.get(owned.getId()) == owned) {
                files.remove(owned.getId());
            }
        }
    }

    @Nullable
    OwnedConfig<?> get(@NotNull ConfigId id) {
        synchronized (files) {
            return files.get(id);
        }
    }

    @NotNull
    List<OwnedConfig<?>> all() {
        synchronized (files) {
            return new ArrayList<OwnedConfig<?>>(files.values());
        }
    }

    /** The files that wait for a type word and lie under {@code where}. */
    @NotNull
    List<OwnedConfig<?>> waitingUnder(@NotNull ConfigAddress where) {
        List<OwnedConfig<?>> waiting = new ArrayList<OwnedConfig<?>>();
        synchronized (files) {
            for (OwnedConfig<?> file : files.values()) {
                if (file.isWaitingForTypes() && where.matches(file.getId())) {
                    waiting.add(file);
                }
            }
        }
        return waiting;
    }
}
