package org.pnfolder.pnlibrary.core.network;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/** The rule for the key of a plugin-message channel: the key itself checks its characters, this checks the namespace. */
final class ChannelName {

    private ChannelName() {
    }

    static void check(@NotNull Key key) {
        if (key.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
            throw new IllegalArgumentException("The namespace minecraft is reserved, use your own: " + key.asString());
        }
    }
}
