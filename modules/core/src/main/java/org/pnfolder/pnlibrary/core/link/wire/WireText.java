package org.pnfolder.pnlibrary.core.link.wire;

/** Text limits of the link messages. */
final class WireText {
    private WireText() {
    }

    /** The first {@code limit} characters of {@code text}. */
    static String take(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
