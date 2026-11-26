package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;

@Placeholders("plain")
public class PlainValues {
    @Placeholder
    public int level() {
        return 12;
    }
}
