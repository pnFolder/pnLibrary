package org.pnfolder.pnlibrary.core.placeholders.examples;

import java.util.UUID;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter;
import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers;

/** An outside placeholder system, such as PlaceholderAPI. The library has one for it; this one shows the shape. */
public class OwnSystem implements PlaceholderAdapter {
    @Override
    public String getId() {
        return "own-system";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** {@code {own-system:time}}-like expressions the library does not know are asked here. */
    @Override
    public Object resolve(UUID playerId, String expression) {
        return expression.equals("time") ? "12:00" : null;
    }

    /** Fills {@code %tokens%} of a finished text. */
    @Override
    public String render(UUID playerId, String text) {
        return text.replace("%time%", "12:00");
    }

    /** Shows the library's placeholders to the system under {@code prefix}; the handle takes them back. */
    @Override
    public AutoCloseable publish(String prefix, PublishedAnswers answers) {
        return new AutoCloseable() {
            @Override
            public void close() {
            }
        };
    }
}
