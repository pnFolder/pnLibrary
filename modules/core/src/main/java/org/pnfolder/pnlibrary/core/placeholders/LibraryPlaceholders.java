package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/** The library's own placeholders, {@code {pnlibrary.*}}, readable by every plugin. */
@Placeholders("pnlibrary")
public final class LibraryPlaceholders {
    private static final long MILLIS_PER_SECOND = 1000L;

    private final long startedAtMillis;
    private final Supplier<Integer> pluginCount;
    private final LibraryStatistics stats;
    private final LibraryServer serverInfo;

    public LibraryPlaceholders(
            @NotNull PlatformIdentity platform,
            long startedAtMillis,
            @NotNull Supplier<Integer> pluginCount,
            @NotNull Supplier<Map<String, Long>> statistics) {
        this.startedAtMillis = startedAtMillis;
        this.pluginCount = pluginCount;
        this.stats = new LibraryStatistics(statistics);
        this.serverInfo = new LibraryServer(platform);
    }

    @Placeholder("Library version")
    public Reply<String> version() {
        return Reply.of(PnLibraryIdentity.VERSION);
    }

    @Placeholder("Registered plugins")
    public Reply<Integer> plugins() {
        return Reply.of(pluginCount.get());
    }

    @Placeholder("Seconds since the library started")
    public Reply<Long> uptime() {
        return Reply.of((System.currentTimeMillis() - startedAtMillis) / MILLIS_PER_SECOND);
    }

    @Placeholder("Counters")
    public Reply<LibraryStatistics> stats() {
        return Reply.next(stats);
    }

    @Placeholder("The server")
    public Reply<LibraryServer> server() {
        return Reply.next(serverInfo);
    }
}
