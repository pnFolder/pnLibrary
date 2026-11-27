package org.pnfolder.pnlibrary.updates.databases.dump;

/** Blank test with the same meaning for the lines of copies and for configuration values. */
public final class Blank {
    private Blank() {
    }

    /** True when {@code text} is empty or holds only white space. */
    public static boolean is(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }
}
