package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

public class BoxLater implements ArgumentConverter<TestBox> {
    @Override
    public String getLabel() {
        return "late box";
    }

    @Override
    public TestBox read(ArgumentInput input) {
        return null;
    }

    @Override
    public CompletionStage<TestBox> readLater(final ArgumentInput input) {
        return CompletableFuture.supplyAsync(() -> new TestBox(input.getText().toUpperCase(Locale.ROOT)));
    }
}
