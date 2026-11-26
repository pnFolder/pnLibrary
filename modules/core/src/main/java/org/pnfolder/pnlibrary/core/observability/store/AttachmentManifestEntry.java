package org.pnfolder.pnlibrary.core.observability.store;

/** Persisted manifest form of a {@link StoredAttachment}, without its storage path; Gson reads and writes the fields. */
final class AttachmentManifestEntry {
    final String id;
    final String observationId;
    final String originalName;
    final String contentType;
    final long size;
    final String sha256;

    AttachmentManifestEntry(StoredAttachment attachment) {
        this.id = attachment.getId();
        this.observationId = attachment.getObservationId();
        this.originalName = attachment.getOriginalName();
        this.contentType = attachment.getContentType();
        this.size = attachment.getSize();
        this.sha256 = attachment.getSha256();
    }

    StoredAttachment restore(java.nio.file.Path storedPath) {
        return new StoredAttachment(id, observationId, originalName, contentType, size, sha256, storedPath);
    }
}
