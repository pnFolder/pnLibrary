package org.pnfolder.pnlibrary.core.lifecycle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Names of the library commands a platform may mount under its own control command. */
public final class MountableCommands {
    public static final String EVENTS = "events";
    public static final String DEBUG = "debug";
    public static final String STATS = "stats";
    public static final String PLACEHOLDERS = "placeholders";
    public static final String SETTINGS = "settings";
    public static final String ACTIONS = "actions";
    public static final String REGISTRY = "registry";
    public static final String COOLDOWNS = "cooldowns";
    public static final String DOWNLOADS = "downloads";

    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            EVENTS, DEBUG, STATS, PLACEHOLDERS, SETTINGS, ACTIONS, REGISTRY, COOLDOWNS, DOWNLOADS));

    private MountableCommands() {
    }
}
