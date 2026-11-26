package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Format;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Viewer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

public class TestClanFormats {
    @Format("pad0")
    public String pad0(int n, @Argument ArgumentValue<Integer> width) {
        StringBuilder text = new StringBuilder(String.valueOf(n));
        while (text.length() < width.required()) {
            text.insert(0, '0');
        }
        return text.toString();
    }

    @Format("lang")
    public String lang(int n, @Viewer ViewerInfo viewer) {
        return n + "/" + viewer.getLanguage();
    }
}
