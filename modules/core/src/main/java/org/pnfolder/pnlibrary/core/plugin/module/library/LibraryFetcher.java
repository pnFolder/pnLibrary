package org.pnfolder.pnlibrary.core.plugin.module.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.LibraryScope;
import org.pnfolder.pnlibrary.api.libraries.LibrarySource;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.spi.libraries.ExternalLibraries;

/** Downloads one library for {@code owner}; tests replace it, the runtime asks the Libby loader of the platform. */
public interface LibraryFetcher {
    /**
     * Tries every place in {@link ModuleLibrary#getAllSources()} in turn, as many as there are. A source is checked against
     * its own sum, or not at all when it has none. The first one that gives a jar wins; when none does, the error lists the
     * reason of each.
     */
    LibraryFetcher THROUGH_LIBBY = new LibraryFetcher() {
        @Override
        public @Nullable ClassLoader fetch(@NotNull ClassLoader loaderOwner, @NotNull String owner, @Nullable Object plugin,
                                           @NotNull ModuleLibrary library) {
            List<String> failures = new ArrayList<>();
            for (LibrarySource source : SourceHealth.order(library.getAllSources())) {
                try {
                    return ExternalLibraries.load(
                            loaderOwner, owner, library.getGroup(), library.getArtifact(), library.getVersion(),
                            library.getClassifier(), source.getSha256(),
                            source.isDirect() ? source.getUrl() : null,
                            source.isDirect() ? Collections.emptyList() : Collections.singletonList(source.getUrl()),
                            library.getScope() == LibraryScope.ISOLATED,
                            library.getScope() == LibraryScope.PLUGIN ? plugin : null);
                } catch (IllegalStateException failure) {
                    SourceHealth.failed(source.getUrl());
                    failures.add(source.getName() + ": " + failure.getMessage());
                }
            }
            String reasons = failures.isEmpty() ? "no source to look in" : join(failures);
            throw new IllegalStateException("Cannot load " + library.getCoordinates() + ": " + reasons);
        }

        private String join(List<String> parts) {
            StringBuilder text = new StringBuilder();
            for (String part : parts) {
                if (text.length() > 0) {
                    text.append("; ");
                }
                text.append(part);
            }
            return text.toString();
        }
    };

    /** Returns the class loader that holds an isolated library, or {@code null} for any other scope. */
    @Nullable
    ClassLoader fetch(@NotNull ClassLoader loaderOwner, @NotNull String owner, @Nullable Object plugin, @NotNull ModuleLibrary library);
}
