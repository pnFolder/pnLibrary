package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** A sender that keeps what it was sent, for command tests. */
public final class RecordingSender implements CommandSender {
    private final Pointers pointers;
    private final List<Component> messages = new ArrayList<>();

    public RecordingSender(String name, Predicate<String> permission) {
        this.pointers = AudienceExtensions.senderPointers(name, permission);
    }

    /** The console: every permission is held. */
    public RecordingSender() {
        this("Console", permission -> true);
    }

    @NotNull
    @Override
    public Pointers pointers() {
        return pointers;
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        messages.add(message);
    }

    public List<Component> messages() {
        return messages;
    }

    /** Everything sent, as plain text, one message per line. */
    public String text() {
        List<String> lines = new ArrayList<>();
        for (Component message : messages) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return String.join("\n", lines);
    }

    public void clear() {
        messages.clear();
    }
}
