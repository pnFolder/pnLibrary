package ru.privatenull.pnlibrary.core.upload

import java.io.IOException

/** Abstraction over a remote report backend (mclo.gs, encrypted storage, …). */
interface ReportUploader {

    /** Stable identifier for the backend, used in the upload ledger. */
    val backendId: String

    /**
     * Uploads [payload] (raw text or encrypted envelope JSON) and returns a receipt.
     *
     * @throws IOException on network or API failures.
     */
    @Throws(IOException::class)
    fun upload(payload: String): UploadReceipt

    /**
     * Deletes a previously uploaded report using the token stored in [receipt].
     *
     * @return `true` if the deletion succeeded, `false` if not supported or already deleted.
     * @throws IOException on network failures.
     */
    @Throws(IOException::class)
    fun delete(receipt: UploadReceipt): Boolean = false
}
