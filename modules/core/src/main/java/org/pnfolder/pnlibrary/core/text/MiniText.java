package org.pnfolder.pnlibrary.core.text;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

/**
 * MiniMessage when the server's Adventure can run it, otherwise {@link MiniMessageLite}. Availability is checked once;
 * only linkage errors count as "too old", any other failure is a real bug and propagates.
 */
public final class MiniText {
    private final Supplier<MiniMessage> factory;
    private final AtomicBoolean warned = new AtomicBoolean(false);
    private boolean probed;
    private MiniMessage mini;
    private LinkageError failure;

    public MiniText() {
        this(MiniMessage::miniMessage);
    }

    public MiniText(@NotNull Supplier<MiniMessage> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public @NotNull Component deserialize(@NotNull String input, @NotNull Consumer<String> warn) {
        MiniMessage available = available(warn);
        return available != null ? available.deserialize(input) : MiniMessageLite.deserialize(input);
    }

    public @NotNull String serialize(@NotNull Component component, @NotNull Consumer<String> warn,
                                     @NotNull Function<Component, String> fallback) {
        MiniMessage available = available(warn);
        return available != null ? available.serialize(component) : fallback.apply(component);
    }

    private MiniMessage available(Consumer<String> warn) {
        probe();
        if (mini != null) {
            return mini;
        }
        if (warned.compareAndSet(false, true)) {
            warn.accept("Adventure сервера слишком старый для MiniMessage, включён упрощённый разбор ("
                + failure.getClass().getSimpleName() + ": " + failure.getMessage() + ")");
        }
        return null;
    }

    private synchronized void probe() {
        if (probed) {
            return;
        }
        try {
            MiniMessage candidate = factory.get();
            candidate.deserialize("<red>x</red>");
            mini = candidate;
        } catch (LinkageError error) {
            failure = error;
        }
        probed = true;
    }
}
