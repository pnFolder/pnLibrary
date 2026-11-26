package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.Locale;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Format;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;

/** The formats of the address: {@code pad0:4} and {@code upper}. They are not in the route of the field, they are applied after it. */
public class WarFormats {
    @Format("pad0")
    public String pad0(int n, @Argument ArgumentValue<Integer> width) {
        StringBuilder text = new StringBuilder(String.valueOf(n));
        while (text.length() < width.required()) {
            text.insert(0, '0');
        }
        return text.toString();
    }

    @Format("upper")
    public String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
