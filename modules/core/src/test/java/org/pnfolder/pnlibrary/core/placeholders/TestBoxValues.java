package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.api.placeholders.annotation.Argument;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

@Placeholders("box")
public class TestBoxValues {
    @Placeholder
    public Reply<String> named(@Argument(converter = BoxByName.class) ArgumentValue<TestBox> box) {
        return Reply.of(box.getValue() != null ? box.getValue().getName() : "failed: " + box.getError());
    }

    @Placeholder
    public Reply<String> later(@Argument(converter = BoxLater.class) ArgumentValue<TestBox> box) {
        return Reply.of(box.required().getName());
    }

    @Placeholder
    public Reply<String> chain(
            @Argument ArgumentValue<String> first,
            @Argument(name = "second", converter = SecondByFirst.class) ArgumentValue<String> second) {
        return Reply.of(second.required());
    }
}
