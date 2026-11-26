package org.pnfolder.pnlibrary.core.observability.report;

/** The parts of one encrypted payload before they are written into an envelope. */
final class EncryptedPayload {
    final String createdUtc;
    final byte[] nonce;
    final byte[] wrappedKey;
    final byte[] ciphertext;

    EncryptedPayload(String createdUtc, byte[] nonce, byte[] wrappedKey, byte[] ciphertext) {
        this.createdUtc = createdUtc;
        this.nonce = nonce;
        this.wrappedKey = wrappedKey;
        this.ciphertext = ciphertext;
    }
}
