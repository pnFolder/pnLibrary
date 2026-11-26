package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;

public class BoxByName implements ArgumentConverter<TestBox> {
    @Override
    public String getLabel() {
        return "box";
    }

    @Override
    public TestBox read(ArgumentInput input) {
        if (input.getText().equals("me")) {
            return new TestBox("mine");
        }
        if (input.getText().equals("none")) {
            return input.<TestBox>fail("no box called none");
        }
        return new TestBox(input.getText());
    }

    @Override
    public List<String> suggest(ArgumentInput input) {
        List<String> words = new ArrayList<String>();
        for (String word : new String[] {"alpha", "beta"}) {
            if (word.startsWith(input.getText())) {
                words.add(word);
            }
        }
        return words;
    }
}
