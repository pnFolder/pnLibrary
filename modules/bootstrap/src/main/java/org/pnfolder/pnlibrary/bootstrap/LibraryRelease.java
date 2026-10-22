package org.pnfolder.pnlibrary.bootstrap;

import java.net.URI;

/** One verified pnLibrary jar of a release: where to get it and what it must hash to. */
public final class LibraryRelease {
    private final String version;
    private final String fileName;
    private final URI uri;
    private final String sha256;
    private final long size;
    private final boolean stable;

    LibraryRelease(String version, String fileName, URI uri, String sha256, long size, boolean stable) {
        this.version = version;
        this.fileName = fileName;
        this.uri = uri;
        this.sha256 = sha256;
        this.size = size;
        this.stable = stable;
    }

    /** Returns the release version.
     * @return version without a leading {@code v} */
    public String version() { return version; }

    /** Returns the jar file name.
     * @return for example {@code pnLibrary-1.0.0-velocity-java17.jar} */
    public String fileName() { return fileName; }

    /** Returns the HTTPS download address.
     * @return the address of the jar, from the repository list or the GitHub release */
    public URI uri() { return uri; }

    /** Returns the SHA-256 the jar must have.
     * @return 64 lowercase hex characters */
    public String sha256() { return sha256; }

    /** Returns the size published for the jar.
     * @return the size in bytes, or {@code -1} when the repository list does not say */
    public long size() { return size; }

    /** Returns a copy with the size that was actually downloaded.
     * @param actual size in bytes
     * @return the same release with a known size */
    LibraryRelease withSize(long actual) { return new LibraryRelease(version, fileName, uri, sha256, actual, stable); }

    /** Returns whether this is a stable release rather than a beta, alpha, dev or release candidate.
     * @return {@code true} for a stable release */
    public boolean stable() { return stable; }
}
