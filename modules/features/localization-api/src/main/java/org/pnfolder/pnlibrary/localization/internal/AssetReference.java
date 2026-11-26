package org.pnfolder.pnlibrary.localization.internal;

/** Hash and size of one asset listed in a Mojang asset index. */
final class AssetReference {
    private final String hash;
    private final int size;

    AssetReference(String hash, int size) {
        this.hash = hash;
        this.size = size;
    }

    String getHash() {
        return hash;
    }

    int getSize() {
        return size;
    }
}
