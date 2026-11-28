package org.pnfolder.pnlibrary.core.menu.data.question;

import org.pnfolder.pnlibrary.core.menu.data.MenuData;
import org.pnfolder.pnlibrary.core.menu.data.MenuCalls;

/** The journal, tasks, statistics, placeholders and settings that another server asks for. */
public final class SystemQuestions {
    private static final int JOURNAL_HOURS = 24;
    private static final int JOURNAL_LIMIT = 200;

    private SystemQuestions() {
    }

    /** Answers {@code ask} when it is one of these questions; false when it is not. */
    public static boolean answer(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.JOURNAL_GROUPS:
                ask.give(data.journalGroups(ask.text("level"), ask.number("hours", JOURNAL_HOURS), ask.number("limit", JOURNAL_LIMIT)));
                return true;
            case MenuCalls.JOURNAL_DETAIL:
                ask.give(data.journalDetail(ask.textOrEmpty("id")));
                return true;
            case MenuCalls.TASKS:
                ask.give(data.tasks());
                return true;
            case MenuCalls.MODULES:
                ask.give(data.modules());
                return true;
            case MenuCalls.LIBRARIES:
                ask.give(data.libraries());
                return true;
            case MenuCalls.DISK:
                ask.give(data.disk());
                return true;
            case MenuCalls.MODULE_ACTION:
                ask.give(data.moduleAction(ask.textOrEmpty("key"), ask.textOrEmpty("action")));
                return true;
            default:
                return more(data, ask);
        }
    }

    private static boolean more(MenuData data, MenuQuestion ask) {
        switch (ask.method()) {
            case MenuCalls.STATS:
                ask.give(data.stats());
                return true;
            case MenuCalls.CHANNELS:
                ask.give(data.channels());
                return true;
            case MenuCalls.PLACEHOLDERS:
                ask.give(data.placeholders());
                return true;
            case MenuCalls.SCENARIO_DEFINITIONS:
                ask.give(data.scenarioDefinitions());
                return true;
            case MenuCalls.RUN_TASK:
                ask.give(data.runTask(ask.textOrEmpty("id")));
                return true;
            case MenuCalls.CANCEL_TASK:
                ask.give(data.cancelTask(ask.textOrEmpty("id")));
                return true;
            case MenuCalls.SETTINGS:
                ask.give(data.settings());
                return true;
            case MenuCalls.CHANGE_SETTING:
                ask.give(data.changeSetting(ask.textOrEmpty("key"), ask.text("value"), ask.actor));
                return true;
            default:
                return false;
        }
    }
}
