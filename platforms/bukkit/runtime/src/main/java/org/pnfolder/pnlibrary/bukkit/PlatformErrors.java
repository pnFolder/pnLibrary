package org.pnfolder.pnlibrary.bukkit;

/** Rethrows what a wait produced without wrapping it, for callers whose interface allows no checked failures. */
final class PlatformErrors {
    private PlatformErrors() {
    }

    static RuntimeException rethrow(Throwable error) {
        return PlatformErrors.<RuntimeException>sneaky(error);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneaky(Throwable error) throws E {
        throw (E) error;
    }
}
