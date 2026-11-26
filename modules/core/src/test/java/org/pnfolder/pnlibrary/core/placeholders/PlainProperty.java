package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;

@Placeholders("plainprop")
public class PlainProperty {
    @Placeholder
    public final String tag = "WLF";
}
