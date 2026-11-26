package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

/** The same type read another way: by a tag of three letters. */
public class ClanByTag implements ArgumentConverter<Clan> {
    @Override
    public String getLabel() {
        return "clan tag";
    }

    @Override
    public Clan read(ArgumentInput input) {
        for (String name : Clans.names()) {
            Clan clan = Clans.byName(name);
            if (clan != null) {
                String tag = clan.getName().substring(0, Math.min(3, clan.getName().length()));
                return tag.equalsIgnoreCase(input.getText()) ? clan : null;
            }
        }
        return null;
    }
}
