package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.address.ParsedArgument;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

/** How a slot reads its text. It never rejects a text: the method decides what to do with a wrong one. */
public final class SlotType {
    private final String label;
    private final List<String> choices;
    private final Function<ArgumentInput, CompletionStage<ParsedArgument>> convert;

    public SlotType(String label, List<String> choices, Function<ArgumentInput, CompletionStage<ParsedArgument>> convert) {
        this.label = label;
        this.choices = choices;
        this.convert = convert;
    }

    String getLabel() {
        return label;
    }

    List<String> getChoices() {
        return choices;
    }

    CompletionStage<ParsedArgument> convert(ArgumentInput input) {
        return convert.apply(input);
    }
}
