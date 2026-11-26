package org.pnfolder.pnlibrary.core.upload;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Serializable remote-deletion instruction retained by {@link UploadLedger}. */
public final class UploadLedgerEntry {
    // Field names are the JSON keys of the ledger file; Gson fills them through the no-argument constructor.
    private String backend = "";
    private String link = "";
    private String id = "";
    private String token = "";
    private long deleteAt = 0L;

    public UploadLedgerEntry() {
    }

    public UploadLedgerEntry(@NotNull String backend, @NotNull String link, @NotNull String id, @NotNull String token,
                             long deleteAt) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.link = Objects.requireNonNull(link, "link");
        this.id = Objects.requireNonNull(id, "id");
        this.token = Objects.requireNonNull(token, "token");
        this.deleteAt = deleteAt;
    }

    public @NotNull String getBackend() {
        return backend;
    }

    public @NotNull String getLink() {
        return link;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getToken() {
        return token;
    }

    public long getDeleteAt() {
        return deleteAt;
    }

    /** Whether all fields required for a safe deletion attempt are present. */
    public boolean isValid() {
        if (blank(backend) || blank(id) || blank(token) || deleteAt <= 0) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(link);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        return "https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
            && uri.getHost() != null && !blank(uri.getHost());
    }

    private static boolean blank(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UploadLedgerEntry)) {
            return false;
        }
        UploadLedgerEntry entry = (UploadLedgerEntry) other;
        return deleteAt == entry.deleteAt && backend.equals(entry.backend) && link.equals(entry.link)
            && id.equals(entry.id) && token.equals(entry.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backend, link, id, token, deleteAt);
    }

    @Override
    public String toString() {
        return "UploadLedgerEntry(backend=" + backend + ", link=" + link + ", id=" + id + ", deleteAt=" + deleteAt + ")";
    }
}
