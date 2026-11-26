package org.pnfolder.pnlibrary.api.text.serializer;

import net.kyori.adventure.text.Component;

/** Joins parsed lines into one multiline component. */
final class ComponentLines {
    private ComponentLines() {
    }

    /** Joins components with {@code Component.newline()} between them, or returns an empty component for none. */
    static Component join(Iterable<Component> lines) {
        Component result = null;
        for (Component line : lines) {
            result = result == null ? line : result.append(Component.newline()).append(line);
        }
        return result == null ? Component.empty() : result;
    }
}
