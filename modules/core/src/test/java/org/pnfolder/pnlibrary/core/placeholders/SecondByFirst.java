package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentConverter;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;

public class SecondByFirst implements ArgumentConverter<String> {
    @Override
    public String getLabel() {
        return "second";
    }

    @Override
    public String read(ArgumentInput input) {
        List<String> before = new ArrayList<String>();
        for (ArgumentValue<?> value : input.getPrevious()) {
            before.add(value.getText());
        }
        return String.join(", ", before) + "/" + input.getPosition() + "/" + input.getName() + "/" + input.getRoute().getField().getText();
    }
}
