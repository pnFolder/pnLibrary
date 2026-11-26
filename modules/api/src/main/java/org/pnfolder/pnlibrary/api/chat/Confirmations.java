package org.pnfolder.pnlibrary.api.chat;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.notice.ConfirmChoice;

/** Pages that ask to confirm a risky action. */
final class Confirmations {
    private final ChatKit kit;

    Confirmations(ChatKit kit) {
        this.kit = kit;
    }

    List<Component> single(String title, List<String> lines, String label, String command, String hint) {
        List<Component> result = intro(title, lines);
        if (kit.getReader().isInteractive()) {
            result.add(kit.text("  ").append(kit.button(label, command, hint, ChatPalette.DANGER)));
        } else {
            result.add(kit.text("  Подтвердить: ", ChatPalette.LABEL).append(kit.text(command, ChatPalette.LABEL)));
        }
        return kit.framed(result);
    }

    List<Component> choices(String title, List<String> lines, List<ConfirmChoice> choices) {
        List<Component> result = intro(title, lines);
        if (kit.getReader().isInteractive()) {
            List<Component> buttons = new ArrayList<Component>();
            for (ConfirmChoice choice : choices) {
                buttons.add(kit.button(choice.getLabel(), choice.getCommand(), choice.getHint(), ChatPalette.DANGER));
            }
            Component row = kit.actions(buttons.toArray(new Component[0]));
            if (row != null) {
                result.add(row);
            }
        } else {
            for (ConfirmChoice choice : choices) {
                result.add(kit.text("  " + choice.getLabel() + ": ", ChatPalette.LABEL)
                    .append(kit.text(choice.getCommand(), ChatPalette.LABEL)));
            }
        }
        return kit.framed(result);
    }

    private List<Component> intro(String title, List<String> lines) {
        List<Component> result = new ArrayList<Component>();
        result.add(kit.header(title));
        result.add(kit.blank());
        for (String line : lines) {
            result.add(kit.note(line, ChatPalette.TEXT));
        }
        result.add(kit.blank());
        return result;
    }
}
