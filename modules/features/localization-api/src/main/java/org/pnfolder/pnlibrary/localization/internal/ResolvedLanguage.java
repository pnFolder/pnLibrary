package org.pnfolder.pnlibrary.localization.internal;

/** Verified bytes of a language file together with its SHA-1 hash. */
final class ResolvedLanguage {
    private final byte[] bytes;
    private final String hash;

    ResolvedLanguage(byte[] bytes, String hash) {
        this.bytes = bytes;
        this.hash = hash;
    }

    byte[] getBytes() {
        return bytes;
    }

    String getHash() {
        return hash;
    }
}
