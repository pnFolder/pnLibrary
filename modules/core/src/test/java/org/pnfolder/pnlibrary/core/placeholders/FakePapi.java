package org.pnfolder.pnlibrary.core.placeholders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

public class FakePapi implements PlaceholderAdapter {
    boolean open = true;
    final Map<String, PublishedAnswers> shown = new HashMap<>();

    @Override
    public String getId() {
        return "placeholderapi";
    }

    @Override
    public boolean isAvailable() {
        return open;
    }

    @Override
    public String render(UUID playerId, String text) {
        return text.replace("%server_name%", "Lobby");
    }

    @Override
    public AutoCloseable publish(final String prefix, PublishedAnswers answers) {
        shown.put(prefix, answers);
        return new AutoCloseable() {
            @Override
            public void close() {
                shown.remove(prefix);
            }
        };
    }
}
