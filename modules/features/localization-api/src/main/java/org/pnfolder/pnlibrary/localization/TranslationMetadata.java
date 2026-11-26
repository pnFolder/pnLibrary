package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Immutable provenance for a loaded locale. */
public final class TranslationMetadata {
    private final TranslationSource source;
    private final String sha1;
    private final boolean stale;

    /** Creates provenance data; {@code sha1} is {@code null} when Mojang provided no digest. */
    public TranslationMetadata(@NotNull TranslationSource source, @Nullable String sha1, boolean stale) {
        this.source = source;
        this.sha1 = sha1;
        this.stale = stale;
    }

    /** Cache or network source used for this table. */
    @NotNull
    public TranslationSource getSource() {
        return source;
    }

    /** Mojang-provided content digest, when available. */
    @Nullable
    public String getSha1() {
        return sha1;
    }

    /** Whether an expired disk entry was used as an offline fallback. */
    public boolean isStale() {
        return stale;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TranslationMetadata)) {
            return false;
        }
        TranslationMetadata that = (TranslationMetadata) other;
        return stale == that.stale && source == that.source && Objects.equals(sha1, that.sha1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, sha1, stale);
    }

    @Override
    public String toString() {
        return "TranslationMetadata(source=" + source + ", sha1=" + sha1 + ", stale=" + stale + ")";
    }
}
