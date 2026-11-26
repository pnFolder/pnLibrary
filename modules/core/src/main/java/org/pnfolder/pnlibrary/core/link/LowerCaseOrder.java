package org.pnfolder.pnlibrary.core.link;

import java.util.Comparator;
import java.util.Locale;

/** Case-insensitive ordering of names. */
final class LowerCaseOrder implements Comparator<String> {
    @Override
    public int compare(String a, String b) {
        return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
    }
}
