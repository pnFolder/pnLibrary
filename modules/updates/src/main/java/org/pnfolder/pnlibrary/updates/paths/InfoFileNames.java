package org.pnfolder.pnlibrary.updates.paths;

import org.jetbrains.annotations.NotNull;

/**
 * The two names of a text file the library writes for administrators: {@code preferred} is in Russian, {@code ascii}
 * is used when the server's JVM cannot create a file with a Russian name (a non-UTF-8 {@code sun.jnu.encoding}).
 *
 * <p>Only names, never paths: building a path from {@code preferred} already fails on such a JVM, which must not
 * stop the library.
 */
public final class InfoFileNames {
    private final String preferred;
    private final String ascii;

    /** Creates the pair of names. */
    public InfoFileNames(@NotNull String preferred, @NotNull String ascii) {
        this.preferred = preferred;
        this.ascii = ascii;
    }

    /** The Russian name. */
    @NotNull
    public String getPreferred() {
        return preferred;
    }

    /** The name that every JVM can create. */
    @NotNull
    public String getAscii() {
        return ascii;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InfoFileNames)) {
            return false;
        }
        InfoFileNames that = (InfoFileNames) other;
        return preferred.equals(that.preferred) && ascii.equals(that.ascii);
    }

    @Override
    public int hashCode() {
        return 31 * preferred.hashCode() + ascii.hashCode();
    }

    @Override
    public String toString() {
        return "InfoFileNames(preferred=" + preferred + ", ascii=" + ascii + ")";
    }
}
