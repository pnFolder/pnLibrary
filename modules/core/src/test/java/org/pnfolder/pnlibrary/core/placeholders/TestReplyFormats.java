package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Format;

public class TestReplyFormats {
    @Format("coins")
    public String coins(long amount) {
        return amount + " coins";
    }
}
