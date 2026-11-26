package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.registry.Visibility;

@Placeholders("shop")
public class ShopHolder {
    @Placeholder("open")
    public Reply<Integer> getPrice() {
        return Reply.of(1);
    }

    @Placeholder(value = "closed", visibility = Visibility.MODULE)
    public Reply<Integer> getCounter() {
        return Reply.of(2);
    }
}
