package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

// Example 8: converters. They turn the text of one argument into an object, also when the class is not yours.
//
// Two ways to attach one:
//   1. On the parameter:  @Argument(converter = ClanByName.class) ArgumentValue<Clan> clan
//   2. For a whole type:  placeholders.converter(Clan.class, new ClanByName())  (used when the parameter names none;
//      it belongs to the module and is removed with it)

/** Reads a clan by its name. {@code Clan} stays as it is: the converter lives beside it. */
public class ClanByName implements ArgumentConverter<Clan> {
    @Override
    public String getLabel() {
        return "clan name";
    }

    @Override
    public Clan read(ArgumentInput input) {
        if (input.getText().equalsIgnoreCase("me")) {
            return Clans.of(input.getViewer().getPlayerId()); // `me`: the clan of who asks
        }
        Clan clan = Clans.byName(input.getText());
        // the reason is kept in ArgumentValue.getError()
        return clan != null ? clan : input.<Clan>fail("there is no clan called '" + input.getText() + "'");
    }

    /** For commands and lists: the words that fit what is typed so far. */
    @Override
    public List<String> suggest(ArgumentInput input) {
        List<String> words = new ArrayList<>();
        for (String name : Clans.names()) {
            if (name.regionMatches(true, 0, input.getText(), 0, input.getText().length())) {
                words.add(name);
            }
        }
        return words;
    }
}
