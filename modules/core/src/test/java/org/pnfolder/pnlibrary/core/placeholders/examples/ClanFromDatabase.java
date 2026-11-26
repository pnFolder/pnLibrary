package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

/** Work that takes time (a database): override {@code readLater}. {@code read} is then not used. */
public class ClanFromDatabase implements ArgumentConverter<Clan> {
    @Override
    public String getLabel() {
        return "clan from the database";
    }

    @Override
    public Clan read(ArgumentInput input) {
        return null;
    }

    @Override
    public CompletionStage<Clan> readLater(final ArgumentInput input) {
        return CompletableFuture.supplyAsync(() -> Clans.byName(input.getText()));
    }
}
