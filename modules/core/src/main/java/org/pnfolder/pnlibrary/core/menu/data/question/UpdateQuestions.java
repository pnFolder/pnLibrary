package org.pnfolder.pnlibrary.core.menu.data.question;

import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.MenuCalls;
import java.time.Duration;
import java.util.UUID;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;

/** Facts and actions of the update catalog that another server asks for. */
public final class UpdateQuestions {
    private UpdateQuestions() {
    }

    /** Answers {@code ask} when it is one of these questions; false when it is not. */
    public static boolean answer(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.VERSION:
                ask.give(data.version());
                return true;
            case MenuCalls.SNAPSHOTS:
                ask.give(data.snapshots());
                return true;
            case MenuCalls.SNAPSHOT:
                ask.give(data.snapshot(ask.textOrEmpty("product")));
                return true;
            case MenuCalls.PLUGINS:
                ask.give(data.plugins());
                return true;
            case MenuCalls.PLAN:
                ask.give(data.plan());
                return true;
            case MenuCalls.HELD_UNTIL:
                ask.give(data.heldUntil(ask.textOrEmpty("product")));
                return true;
            default:
                return history(data, ask) || changes(data, ask);
        }
    }

    private static boolean history(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.HISTORY_COUNT:
                ask.finish(data.historyCount(ask.text("product")));
                return true;
            case MenuCalls.HISTORY_PAGE:
                ask.finish(data.historyPage(ask.number("page", 1), ask.text("product")));
                return true;
            case MenuCalls.BACKUPS:
                ask.finish(data.backups());
                return true;
            default:
                return false;
        }
    }

    private static boolean changes(MenuData data, MenuQuestion ask) {
        String product = ask.textOrEmpty("product");
        switch (ask.method()) {
            case MenuCalls.CHECK_ALL:
                ask.finish(data.checkAll(ask.actor));
                return true;
            case MenuCalls.CHECK_ONE:
                data.checkOne(product, ask.actor);
                ask.give(true);
                return true;
            case MenuCalls.FORCE_LIBRARY:
                ask.finish(data.forceLibrary(ask.actor));
                return true;
            case MenuCalls.SET_CHANNEL:
                ask.finish(data.setChannel(product, channel(ask.text("channel")), ask.actor));
                return true;
            case MenuCalls.SET_JAVA_BUILD:
                ask.finish(data.setJavaBuild(product, ask.optionalNumber("java"), ask.actor));
                return true;
            case MenuCalls.SET_HELD:
                Long millis = ask.optionalLong("millis");
                ask.finish(data.setHeld(product, millis == null ? null : Duration.ofMillis(millis), ask.actor));
                return true;
            default:
                return backups(data, ask);
        }
    }

    private static boolean backups(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.ROLLBACK:
                ask.finish(data.rollback(UUID.fromString(ask.text("id")), ask.flag("restore"), ask.actor));
                return true;
            case MenuCalls.RESTORE_DATABASE:
                ask.finish(data.restoreDatabase(UUID.fromString(ask.text("id")), required(ask, "product"),
                    required(ask, "name"), ask.actor));
                return true;
            case MenuCalls.EXTRACT_BACKUP:
                ask.finish(data.extractBackup(UUID.fromString(ask.text("id")), required(ask, "product"), ask.actor));
                return true;
            default:
                return false;
        }
    }

    private static UpdateChannel channel(String name) {
        if (name == null) {
            return null;
        }
        for (UpdateChannel channel : UpdateChannel.values()) {
            if (channel.name().equals(name)) {
                return channel;
            }
        }
        return null;
    }

    private static String required(MenuQuestion ask, String name) {
        String value = ask.text(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument is missing: " + name);
        }
        return value;
    }
}
