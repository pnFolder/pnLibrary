package org.pnfolder.pnlibrary.core.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** A sender with chosen permissions that keeps the messages and sounds it was sent. */
final class TestCommandSender implements CommandSender {
    final List<Component> messages = new ArrayList<>();
    final List<Sound> sounds = new ArrayList<>();
    private final Set<String> permissions;
    private final boolean console;

    TestCommandSender(Set<String> permissions, boolean console) {
        this.permissions = permissions;
        this.console = console;
    }

    TestCommandSender(Set<String> permissions) {
        this(permissions, false);
    }

    TestCommandSender(boolean console) {
        this(Collections.emptySet(), console);
    }

    TestCommandSender() {
        this(false);
    }

    @Override
    public void playSound(@NotNull Sound sound) {
        sounds.add(sound);
    }

    @NotNull
    @Override
    public Pointers pointers() {
        return AudienceExtensions.senderPointers("sender", console ? null : UUID.randomUUID(), permission -> permissions.contains(permission));
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        messages.add(message);
    }

    /** The plain text of every message, without the mark that opens a notice. */
    List<String> texts() {
        List<String> texts = new ArrayList<>();
        for (Component message : messages) {
            texts.add(textOf(message));
        }
        return texts;
    }

    /** The text of a message without the mark that opens a notice. */
    static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).replaceFirst("^ {2}\\S ", "");
    }
}
