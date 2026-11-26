package org.pnfolder.pnlibrary.core.commands.modules;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.libraries.LibrarySource;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryUsage;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

/** Reads every library that plugins and modules registered and turns each use into a {@link LibraryRow}. */
final class LibraryScan {
    private LibraryScan() {
    }

    /** Every library of every plugin and module of this server; {@code locate} finds the jar of a library. */
    @NotNull
    static List<LibraryRow> all(@NotNull PnLibrary library, @NotNull Function<String, String> locate) {
        List<LibraryUsage> usage = library.getPlugins().libraryUsage();
        Map<String, Set<String>> versions = new HashMap<String, Set<String>>();
        for (LibraryUsage use : usage) {
            Set<String> known = versions.get(identity(use.getLibrary()));
            if (known == null) {
                known = new HashSet<String>();
                versions.put(identity(use.getLibrary()), known);
            }
            known.add(use.getLibrary().getVersion());
        }
        List<LibraryRow> rows = new ArrayList<LibraryRow>();
        for (LibraryUsage use : usage) {
            rows.add(row(use, versions.get(identity(use.getLibrary())).size() > 1, locate));
        }
        Collections.sort(rows, new Comparator<LibraryRow>() {
            @Override
            public int compare(LibraryRow left, LibraryRow right) {
                int byOwner = left.getOwner().compareTo(right.getOwner());
                return byOwner != 0 ? byOwner : left.getTitle().compareTo(right.getTitle());
            }
        });
        return rows;
    }


    private static String identity(ModuleLibrary item) {
        return item.getGroup() + "\u0000" + item.getArtifact();
    }


    private static LibraryRow row(LibraryUsage use, boolean conflict, Function<String, String> locate) {
        ModuleLibrary item = use.getLibrary();
        LibraryStatus status = use.getStatus();
        String file = locate.apply(item.getCoordinates());
        String trust = signed(item) ? LibraryRow.GIVEN : LibraryRow.NONE;
        List<String> repositories = new ArrayList<String>();
        for (LibrarySource source : item.getAllSources()) {
            repositories.add(source.getUrl());
        }
        long size = -1;
        if (file != null && new File(file).isFile()) {
            size = new File(file).length();
        }
        return new LibraryRow(use.getOwner(), item.getTitle(), item.getCoordinates(), item.getVersion(), item.getDescription(),
                repositories, item.isIsolated(), item.isRequired(), !trust.equals(LibraryRow.NONE), conflict, file, size, trust,
                item.getScope().name(), file == null ? null : LibraryRow.placeOf(file),
                status == null ? "LOADED" : status.getState().name().toUpperCase(Locale.ROOT),
                status == null || status.isAutoLoad(), status == null ? null : status.getError(),
                status != null && status.isRestartRequired());
    }


    private static boolean signed(ModuleLibrary item) {
        if (item.getSources().isEmpty()) {
            return false;
        }
        for (LibrarySource source : item.getSources()) {
            if (source.getSha256() == null) {
                return false;
            }
        }
        return true;
    }
}
