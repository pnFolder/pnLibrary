package org.pnfolder.pnlibrary.core.upload;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Receipt returned by a successful upload operation. */
public final class UploadReceipt {
    private final URI link;
    private final String id;
    private final String deleteToken;
    private final long createdEpochSeconds;
    private final long expiresEpochSeconds;
    private final String backend;

    /**
     * @param link the public link for viewing the report
     * @param id backend-specific report identifier
     * @param deleteToken token for later deletion, blank if the backend does not support it
     * @param createdEpochSeconds upload timestamp in epoch seconds, 0 if unavailable
     * @param expiresEpochSeconds expiry timestamp in epoch seconds, 0 if unavailable
     * @param backend backend identifier, for example {@code "mclogs"}
     */
    public UploadReceipt(@NotNull URI link, @NotNull String id, @NotNull String deleteToken, long createdEpochSeconds,
                         long expiresEpochSeconds, @NotNull String backend) {
        this.link = Objects.requireNonNull(link, "link");
        this.id = Objects.requireNonNull(id, "id");
        this.deleteToken = Objects.requireNonNull(deleteToken, "deleteToken");
        this.createdEpochSeconds = createdEpochSeconds;
        this.expiresEpochSeconds = expiresEpochSeconds;
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /** A receipt for a backend that gives no delete token and no expiry; the upload counts as made now. */
    public static @NotNull UploadReceipt permanent(@NotNull URI link, @NotNull String id, @NotNull String backend) {
        return new UploadReceipt(link, id, "", Instant.now().getEpochSecond(), 0, backend);
    }

    public @NotNull URI getLink() {
        return link;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDeleteToken() {
        return deleteToken;
    }

    public long getCreatedEpochSeconds() {
        return createdEpochSeconds;
    }

    public long getExpiresEpochSeconds() {
        return expiresEpochSeconds;
    }

    public @NotNull String getBackend() {
        return backend;
    }

    /** Whether the provider gave a token that deletes the upload. */
    public boolean canDelete() {
        for (int i = 0; i < deleteToken.length(); i++) {
            if (!Character.isWhitespace(deleteToken.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UploadReceipt)) {
            return false;
        }
        UploadReceipt receipt = (UploadReceipt) other;
        return createdEpochSeconds == receipt.createdEpochSeconds && expiresEpochSeconds == receipt.expiresEpochSeconds
            && link.equals(receipt.link) && id.equals(receipt.id) && deleteToken.equals(receipt.deleteToken)
            && backend.equals(receipt.backend);
    }

    @Override
    public int hashCode() {
        return Objects.hash(link, id, deleteToken, createdEpochSeconds, expiresEpochSeconds, backend);
    }

    @Override
    public String toString() {
        return link.toString();
    }
}
