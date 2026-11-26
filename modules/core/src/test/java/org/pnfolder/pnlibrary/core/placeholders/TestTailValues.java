package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("tail")
public class TestTailValues {
    @Placeholder(onInvalid = Invalid.DEFAULT, defaultText = "-")
    public Reply<TestRouteBranch> lost() {
        return Reply.none();
    }
}
