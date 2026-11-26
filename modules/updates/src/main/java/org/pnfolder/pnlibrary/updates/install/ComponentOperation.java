package org.pnfolder.pnlibrary.updates.install;

/** Operation names the component ledger writes to the update history. */
public final class ComponentOperation {
    public static final String ADDED = "COMPONENT_ADDED";
    public static final String REMOVED = "COMPONENT_REMOVED";
    public static final String VERSION_CHANGED = "VERSION_CHANGED";
    public static final String SETTINGS_CHANGED = "SETTINGS_CHANGED";

    private ComponentOperation() {
    }
}
