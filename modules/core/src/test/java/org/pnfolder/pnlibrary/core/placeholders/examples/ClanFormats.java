package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.Locale;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Format;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;

// Example 3: formats, who is asking, and how to connect everything.

/** Formats are methods marked with @Format. {@code {clan.level|rank}} gives #12, {@code {clan.level|pad0:4}} gives 0012. */
public class ClanFormats {

    /** The first parameter is the value; the format is used as {@code |rank}. */
    @Format("rank")
    public String rank(int n) {
        return "#" + n;
    }

    /** Parameters after the first are what is written after the colon: {@code |pad0:4}. */
    @Format("pad0")
    public String pad0(int n, @Argument ArgumentValue<Integer> width) {
        StringBuilder text = new StringBuilder(String.valueOf(n));
        while (text.length() < width.required()) {
            text.insert(0, '0');
        }
        return text.toString();
    }

    /** A format that knows nothing but the number: {@code |money} gives 1 250 000 $. */
    @Format("money")
    public String money(long n) {
        return String.format(Locale.getDefault(), "%,d", n).replace(',', ' ') + " $";
    }
}
