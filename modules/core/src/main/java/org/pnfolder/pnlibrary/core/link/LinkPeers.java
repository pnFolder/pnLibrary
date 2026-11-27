package org.pnfolder.pnlibrary.core.link;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;
import org.pnfolder.pnlibrary.core.link.wire.LinkHello;

/**
 * The other ends this one has said hello to, and whether their message formats match ours. A peer with other formats
 * cannot be talked to, so it stays an important announcement until its hello matches again.
 */
public final class LinkPeers {
    private final Map<String, String> problems = new ConcurrentHashMap<>();

    /**
     * Takes {@code hello} from {@code peer}. Returns what to write to the console when the situation changed (the
     * peer does not match, or matches again), and null when there is nothing new.
     */
    public @Nullable String meet(@NotNull String peer, @NotNull LinkHello hello, @NotNull String ownVersion) {
        if (LinkCodec.PROFILE.equals(hello.getProfile())) {
            return problems.remove(peer) == null ? null : peer + ": версии pnLibrary снова совпадают, обмен данными работает.";
        }
        String text = describe(hello, ownVersion);
        return text.equals(problems.put(peer, text)) ? null : peer + ": " + text;
    }

    /** The peers that cannot be talked to, each with what is wrong. */
    public @NotNull Map<String, String> problems() {
        return new LinkedHashMap<>(problems);
    }

    private static String describe(LinkHello hello, String ownVersion) {
        return "версия связи " + hello.getProfile() + " (pnLibrary " + hello.getLibraryVersion() + "), здесь "
            + LinkCodec.PROFILE + " (pnLibrary " + ownVersion + "). Обмен данными между ними не работает: "
            + "обновите pnLibrary на прокси и на всех серверах до одной версии.";
    }
}
