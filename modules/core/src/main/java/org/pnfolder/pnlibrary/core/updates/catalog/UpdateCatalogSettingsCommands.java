package org.pnfolder.pnlibrary.core.updates.catalog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.CommandSender;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.core.exceptions.Throwables;
import org.pnfolder.pnlibrary.updates.engine.FreezeDuration;

/**
 * The {@code set <plugin> channel|auto|hold} commands of the catalog: each changes one setting of a component and
 * then shows the refreshed settings card with a confirmation.
 */
final class UpdateCatalogSettingsCommands {
    static final List<String> SWITCH_SUGGESTIONS = Collections.unmodifiableList(Arrays.asList("on", "off"));
    static final String DEFAULT_CHANNEL = "default";
    private static final Set<String> ON = new HashSet<>(Arrays.asList("on", "true", "yes", "1", "вкл"));
    private static final Set<String> OFF = new HashSet<>(Arrays.asList("off", "false", "no", "0", "выкл"));
    private static final Duration DEFAULT_HOLD = Duration.ofDays(7);
    /** Whether only the {@code dev} channel can be chosen; set to false when more channels are published. */
    private static final boolean ONLY_DEV = true;

    private final PnLibrary library;
    private final Predicate<CommandContext> ready;
    private final Function<CommandContext, UpdateCatalogRoutes> routesFor;
    private final Consumer<Runnable> deliver;
    private final java.util.function.BiConsumer<CommandContext, UpdateCatalogCommands.SettingSaved> onSaved;

    /**
     * @param ready returns whether the command may run for this sender; it reports the reason itself
     * @param routesFor the routes adapted to the sender
     */
    UpdateCatalogSettingsCommands(PnLibrary library, Predicate<CommandContext> ready,
                                  Function<CommandContext, UpdateCatalogRoutes> routesFor, Consumer<Runnable> deliver,
                                  java.util.function.BiConsumer<CommandContext, UpdateCatalogCommands.SettingSaved> onSaved) {
        this.library = library;
        this.ready = ready;
        this.routesFor = routesFor;
        this.deliver = deliver;
        this.onSaved = onSaved;
    }

    /** The channels an administrator may choose; only {@code dev} is published for now, so the others stay switched off. */
    List<UpdateChannel> available() {
        return ONLY_DEV ? Collections.singletonList(UpdateChannel.DEV) : UpdateCatalogView.CHANNELS;
    }

    private static String lower(UpdateChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT);
    }

    /** Sets the release channel, or removes the administrator's choice with {@code default} so the plugin decides again. */
    void setChannel(CommandContext context) {
        String value = context.get("value");
        if (value.equalsIgnoreCase(DEFAULT_CHANNEL)) {
            change(context, "Канал", snapshot -> lower(snapshot.getChannel()), "как задал плагин",
                (product, actor) -> library.getUpdates().getSettings().channel(product, null, actor));
            return;
        }
        UpdateChannel channel = null;
        for (UpdateChannel candidate : UpdateCatalogView.CHANNELS) {
            if (candidate.name().equalsIgnoreCase(value)) {
                channel = candidate;
            }
        }
        if (channel == null) {
            List<String> names = new ArrayList<>();
            for (UpdateChannel candidate : available()) {
                names.add(lower(candidate));
            }
            context.getSender().sendMessage(CatalogErrors.line("Неизвестный канал " + value + ". Доступны: " + String.join(", ", names) + "."));
            return;
        }
        if (!available().contains(channel)) {
            context.getSender().sendMessage(CatalogErrors.line(
                "Сейчас работает только канал dev, остальные пока не выпускаются. Выберите dev или default."));
            return;
        }
        UpdateChannel chosen = channel;
        change(context, "Канал", snapshot -> lower(snapshot.getChannel()), lower(chosen),
            (product, actor) -> library.getUpdates().getSettings().channel(product, chosen, actor));
    }

    /** Chooses the Java build of a release that has several, or {@code default} to let the library pick one. */
    void setJava(CommandContext context) {
        String value = context.get("value");
        Integer java = null;
        try {
            java = Integer.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            if (!value.equalsIgnoreCase(DEFAULT_CHANNEL)) {
                context.getSender().sendMessage(CatalogErrors.line("Укажите версию Java, например 17, или default."));
                return;
            }
        }
        Integer build = java;
        change(context, "Сборка Java", snapshot -> snapshot.getJavaBuild() == null ? "подбирается" : snapshot.getJavaBuild().toString(),
            build == null ? "подбирается" : build.toString(),
            (product, actor) -> library.getUpdates().getSettings().javaBuild(product, build, actor));
    }

    /** Toggles automatic downloads. */
    void setAutomatic(CommandContext context) {
        Boolean enabled = switchOf(context);
        if (enabled == null) {
            return;
        }
        change(context, "Автозагрузка", snapshot -> snapshot.getAutomaticDownload() ? "включена" : "выключена",
            enabled ? "включена" : "выключена", (product, actor) -> library.getUpdates().getSettings().automaticDownload(product, enabled, actor));
    }

    /** Turns automatic download on or off for all plugins at once, whatever was chosen for single ones. */
    void setAllAutomatic(CommandContext context) {
        Boolean enabled = switchOf(context);
        if (enabled == null || !ready.test(context)) {
            return;
        }
        CommandSender sender = context.getSender();
        UpdateActor actor = UpdateCatalogSupport.actorOf(sender);
        library.getUpdates().getSettings().automaticDownloadForAll(enabled, actor).whenComplete((done, failure) -> deliver.accept(() -> {
            if (failure != null) {
                String reason = Throwables.rootCause(failure).getMessage();
                sender.sendMessage(CatalogErrors.line(reason != null ? reason : "Не удалось изменить настройку."));
            } else {
                sender.sendMessage(Component.text("Сохранено. Автозагрузка для всех плагинов: " + (enabled ? "включена" : "выключена") + ".",
                    ChatPalette.OK));
            }
        }));
    }

    /** Pauses updates for a period such as {@code 3d} ({@code on} means seven days), or lifts the pause with {@code off}. */
    void setHeld(CommandContext context) {
        String value = ((String) context.get("value")).toLowerCase(Locale.ROOT);
        Duration duration;
        if (OFF.contains(value)) {
            duration = null;
        } else if (ON.contains(value)) {
            duration = DEFAULT_HOLD;
        } else {
            try {
                duration = FreezeDuration.parse(value);
            } catch (RuntimeException invalid) {
                context.getSender().sendMessage(CatalogErrors.line("Пауза — от 1m до 7d (например 3d) или off, получено: " + value + "."));
                return;
            }
        }
        Duration period = duration;
        change(context, "Пауза", snapshot -> snapshot.getState() == UpdateState.FROZEN ? "на паузе" : "нет",
            period == null ? "нет" : RussianText.period(period), (product, actor) -> period == null
                ? library.getUpdates().getSettings().release(product, actor)
                : library.getUpdates().getSettings().hold(product, period, actor));
    }

    private Boolean switchOf(CommandContext context) {
        String value = context.get("value");
        String lower = value.toLowerCase(Locale.ROOT);
        if (ON.contains(lower)) {
            return true;
        }
        if (OFF.contains(lower)) {
            return false;
        }
        context.getSender().sendMessage(CatalogErrors.line("Укажите on или off, получено: " + value + "."));
        return null;
    }

    /** Applies a setting, then confirms it below the refreshed settings card, or shows why it was refused. */
    private void change(CommandContext context, String setting, Function<UpdateSnapshot, String> current, String value,
                        BiFunction<String, UpdateActor, CompletionStage<UpdateSnapshot>> apply) {
        if (!ready.test(context)) {
            return;
        }
        String product = context.get("plugin");
        CommandSender sender = context.getSender();
        org.pnfolder.pnlibrary.api.updates.UpdateRegistration known = library.getUpdates().getProducts().get(product);
        String before = known == null ? value : current.apply(known.getSnapshot());
        apply.apply(product, UpdateCatalogSupport.actorOf(sender)).whenComplete((snapshot, failure) -> deliver.accept(() -> {
            if (failure != null) {
                String reason = Throwables.rootCause(failure).getMessage();
                sender.sendMessage(CatalogErrors.line(reason != null ? reason : "Не удалось изменить настройку."));
                return;
            }
            UpdateCatalogRoutes routes = routesFor.apply(context);
            String arrow = routes.getAscii() ? "->" : "→";
            List<Component> lines = new ArrayList<>(UpdateCatalogView.settings(snapshot, routes,
                library.getUpdates().getSettings().heldUntil(snapshot.getProduct())));
            lines.addAll(UpdateCatalogView.notice(new ChatNotice(ChatTone.SUCCESS,
                "Сохранено. " + setting + ": " + before + " " + arrow + " " + value + "."), routes));
            for (Component line : lines) {
                sender.sendMessage(line);
            }
            onSaved.accept(context, new UpdateCatalogCommands.SettingSaved(snapshot.getProduct(), setting, before, value));
        }));
    }
}
