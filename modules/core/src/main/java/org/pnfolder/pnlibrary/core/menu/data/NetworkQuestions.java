package org.pnfolder.pnlibrary.core.menu.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;

/** Owners, server groups and chat commands that another server asks for. */
final class NetworkQuestions {
    private NetworkQuestions() {
    }

    /** Answers {@code ask} when it is one of these questions; false when it is not. */
    static boolean answer(MenuData data, MenuQuestion ask, BiPredicate<String, String> holds) {
        switch (ask.method()) {
            case MenuCalls.OWNER_NAMED:
                ask.give(data.ownerNamed(ask.textOrEmpty("name")));
                return true;
            case MenuCalls.GROUPS:
                ask.give(data.groups());
                return true;
            case MenuCalls.EDIT_GROUP:
                editGroup(data, ask);
                return true;
            case MenuCalls.COMMAND:
                command(data, ask, holds);
                return true;
            default:
                return false;
        }
    }

    private static void editGroup(MenuData data, MenuQuestion ask) {
        GroupOp op = null;
        for (GroupOp candidate : GroupOp.values()) {
            if (candidate.name().equals(ask.text("op"))) {
                op = candidate;
            }
        }
        if (op == null) {
            ask.fail("Неизвестное изменение группы.");
            return;
        }
        ask.give(data.editGroup(new GroupEdit(op, ask.textOrEmpty("group"), ask.text("server"), ask.text("plugin"))));
    }

    private static void command(MenuData data, MenuQuestion ask, BiPredicate<String, String> holds) {
        String viewerName = ask.call.getViewer();
        Set<String> permissions = new LinkedHashSet<>();
        for (String permission : MenuPermissions.ALL) {
            if (holds.test(viewerName, permission)) {
                permissions.add(permission);
            }
        }
        ViewerInfo viewer = new ViewerInfo(viewerName.isEmpty() ? "игрок" : viewerName, permissions);
        ask.give(MenuWire.encodeLines(data.command(viewer, ask.textOrEmpty("line"))));
    }
}
