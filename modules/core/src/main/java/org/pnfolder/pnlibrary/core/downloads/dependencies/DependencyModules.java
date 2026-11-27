package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Which modules of a plugin declared dependencies, so the console cards can say {@code plugin@module}. */
public final class DependencyModules {
    private final Map<Object, Set<String>> modules = Collections.synchronizedMap(new IdentityHashMap<>());

    /** Remembers that the module {@code module} of {@code owner} declared dependencies. */
    public void add(@NotNull Object owner, @NotNull String module) {
        synchronized (modules) {
            modules.computeIfAbsent(owner, key -> new LinkedHashSet<>()).add(module);
        }
    }

    /** The modules of {@code owner} that declared dependencies, comma separated, or {@code null} when none did. */
    public @Nullable String of(@NotNull Object owner) {
        synchronized (modules) {
            Set<String> known = modules.get(owner);
            return known == null || known.isEmpty() ? null : String.join(",", known);
        }
    }
}
