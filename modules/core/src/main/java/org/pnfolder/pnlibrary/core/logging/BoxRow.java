package org.pnfolder.pnlibrary.core.logging;

/** One line of a message box: its status word, label, detail and the error behind a failure. */
final class BoxRow {
    final String status;
    final String label;
    final String detail;
    final Throwable error;

    BoxRow(String status, String label, String detail, Throwable error) {
        this.status = status;
        this.label = label;
        this.detail = detail;
        this.error = error;
    }
}
