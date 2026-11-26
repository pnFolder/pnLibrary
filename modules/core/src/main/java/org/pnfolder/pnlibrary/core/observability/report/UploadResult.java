package org.pnfolder.pnlibrary.core.observability.report;

import org.pnfolder.pnlibrary.core.upload.UploadReceipt;

/** Outcome of the optional upload: a receipt, an error message, or neither when nothing was uploaded. */
final class UploadResult {
    final UploadReceipt receipt;
    final String error;

    UploadResult(UploadReceipt receipt, String error) {
        this.receipt = receipt;
        this.error = error;
    }
}
