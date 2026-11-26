package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Invalid;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder;
import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholders;
import org.pnfolder.pnlibrary.api.placeholders.reply.Reply;

// Example 4: what a method may return, and placeholders that have no class.

/** {@code {server.*}}: values that do not depend on who asks. */
@Placeholders("server")
public class ServerValues {

    /** A number, text, boolean, enum, UUID or {@link Duration} works as it is. {@code {server.online}} gives 3. */
    @Placeholder("Players online")
    public Reply<Integer> online() {
        return Reply.of(3);
    }

    /** A {@link Duration} is shown by the format {@code left}: {@code {server.restart|left}}. */
    @Placeholder("Time until the restart")
    public Reply<Duration> restart() {
        return Reply.of(Duration.ofMinutes(95));
    }

    /** A value that takes time may come later: return a CompletableFuture, the caller is not blocked. */
    @Placeholder("A value calculated in the background")
    public CompletableFuture<Reply<Long>> background() {
        return CompletableFuture.supplyAsync(() -> Reply.of(42L));
    }

    /** A nested object: {@code {server.owner.name}} and {@code {server.owner.since}}. */
    @Placeholder("The owner of the server")
    public Reply<Owner> owner() {
        return Reply.next(new Owner("Steve", 2020));
    }

    /** A branch may come later too: {@code {server.stats.cpu}}. The next methods of the address wait for it, nobody is blocked. */
    @Placeholder("Numbers from the database")
    public CompletableFuture<Reply<ServerStats>> stats() {
        return CompletableFuture.supplyAsync(() -> Reply.next(new ServerStats()));
    }

    /** Without a value the text can show nothing instead of the address: {@link Invalid#EMPTY}. */
    @Placeholder(onInvalid = Invalid.EMPTY)
    public Reply<String> motd() {
        return Reply.of(null);
    }

    /** Or a fixed text: {@link Invalid#DEFAULT} shows the default text of the annotation. */
    @Placeholder(onInvalid = Invalid.DEFAULT, defaultText = "unknown")
    public Reply<String> region() {
        return Reply.of(null);
    }
}
