package org.pnfolder.pnlibrary.core.link.remote;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec;

/** Sends update controls to another server, from a game server through the proxy or from the proxy itself. */
public final class RemoteControl {
    private final Link link;

    public RemoteControl(@NotNull Link link) {
        this.link = Objects.requireNonNull(link, "link");
    }

    /**
     * Asks {@code target} to do {@code action}. {@code onResult} gets the answer, or a failure after a timeout.
     *
     * @return null when the request was sent, otherwise why it could not be
     */
    public @Nullable String send(@NotNull String target, @NotNull RemoteAction action, @Nullable String product,
                                 @NotNull String actor, @NotNull Consumer<RemoteResult> onResult) {
        byte[] key = link.getKey();
        if (key == null) {
            return "Связь выключена: нет файла link.key.";
        }
        boolean named = product != null && !product.trim().isEmpty();
        if (action.getNeedsProduct() && !named) {
            return "Нужно указать плагин.";
        }
        boolean fromProxy = link.getOnProxy();
        RemoteRequest request = new RemoteRequest(UUID.randomUUID(), target,
            fromProxy ? LinkNames.PROXY_ORIGIN : "", actor, action, named ? product : null, Instant.now());
        byte[] bytes = LinkCodec.encodeRequest(request, key);
        link.getReplies().expect(request.getId(), onResult);
        if (!link.send(target, bytes)) {
            link.getReplies().cancel(request.getId());
            return fromProxy ? "Сервер " + target + " недоступен."
                : "Некому передать запрос: на сервере нет игроков онлайн.";
        }
        return null;
    }
}
