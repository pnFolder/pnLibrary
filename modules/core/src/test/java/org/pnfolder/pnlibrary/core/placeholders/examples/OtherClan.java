package org.pnfolder.pnlibrary.core.placeholders.examples;

import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

/** Looks at what stands before it and at the whole address. {@code {clanlist.compare.Wolves.Owls}}: the second reads the first. */
public class OtherClan implements ArgumentConverter<Clan> {
    @Override
    public String getLabel() {
        return "another clan";
    }

    @Override
    public Clan read(ArgumentInput input) {
        String first = input.getPrevious().isEmpty() ? null : input.getPrevious().get(0).getText(); // "Wolves": the arguments to the left
        String field = input.getRoute().getField() == null ? null : input.getRoute().getField().getText(); // "compare": the asked field
        int place = input.getPosition(); // 1: the second argument of the address
        if (input.getText().equalsIgnoreCase(first)) {
            input.<Void>fail(field + " needs two different clans (" + place + ")");
        }
        return Clans.byName(input.getText());
    }
}
