package org.pnfolder.pnlibrary.core.updates.catalog.screens;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.pnfolder.pnlibrary.common.lang.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.page.CommandHint;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.text.RussianText;
import org.pnfolder.pnlibrary.updates.policy.UpdateChannelLabels;

/** Settings card of one component: channel, automatic download and a pause for a chosen period. */
public final class ProductSettingsScreen extends CatalogStyle {
    public ProductSettingsScreen(UpdateCatalogRoutes routes, Instant now, ZoneId zone) {
        super(routes, now, zone);
    }

    /** Channel, automatic download and a pause of at most a week, each choice a button. */
    public List<Component> settings(UpdateSnapshot snapshot, @Nullable Instant heldUntil) {
        UpdateCatalogRoutes routes = getRoutes();
        String product = snapshot.getProduct();
        List<Component> result = new ArrayList<>();
        result.add(header(product, text("настройки", ChatPalette.LABEL)));
        result.add(text("  " + version(snapshot.getCurrentVersion()), ChatPalette.SOFT).append(dot()).append(channelTag(snapshot.getChannel())));
        if (routes.getInteractive() && !routes.getEditable()) {
            result.add(note("Только просмотр: нет права менять настройки."));
        }
        if (isClickable()) {
            result.add(note("Нажмите на кнопку, чтобы выбрать. " + glyph('✔') + " — выбрано сейчас."));
            result.add(note("Пауза — не дольше 5 дней."));
        }
        result.add(blank());
        List<Pair<UpdateChannel, String>> channels = new ArrayList<>();
        for (UpdateChannel channel : UpdateCatalogView.CHANNELS) {
            channels.add(new Pair<>(channel, UpdateChannelLabels.commandName(channel)));
        }
        result.add(choices("Канал", channels, snapshot.getChannel(), UpdateChannelLabels::color, channel ->
            new Pair<>(routes.set(product, "channel", UpdateChannelLabels.commandName(channel)), UpdateChannelLabels.hint(channel))));
        if (isClickable()) {
            Component line = actions(button("[Канал плагина]", routes.set(product, "channel", "default"),
                "Убрать выбор администратора: канал определяет сам плагин", ChatPalette.SOFT));
            if (line != null) {
                result.add(line);
            }
        }
        result.addAll(holdRows(product, snapshot.getState() == UpdateState.FROZEN || heldUntil != null, heldUntil));
        result.add(blank());
        Component line = actions(
            button("[Карточка]", routes.info(product), "Сведения о плагине", ChatPalette.ACCENT),
            button("[История]", routes.history(product), "События только этого плагина", ChatPalette.ACCENT),
            button("[Назад]", routes.getList(), "К списку плагинов", ChatPalette.SOFT));
        if (line != null) {
            result.add(line);
        }
        if (!routes.getInteractive() || !routes.getEditable()) {
            List<String> names = new ArrayList<>();
            for (UpdateChannel channel : UpdateCatalogView.CHANNELS) {
                names.add(UpdateChannelLabels.commandName(channel));
            }
            result.addAll(commandHints(
                new CommandHint(routes.set(product, "channel", "<" + String.join("|", names) + "|default>"), "канал (default: как задал плагин)"),
                new CommandHint(routes.set(product, "hold", "<" + String.join("|", UpdateCatalogView.HOLD_PERIODS) + "|off>"), "пауза")));
        }
        return framed(result);
    }

    private String holdText(@Nullable Instant until) {
        return until == null ? "не задана" : "до " + fullTime(until) + " · " + RussianText.remaining(Duration.between(getNow(), until));
    }

    /** The current pause with a button to lift it, and the periods to pause for or to replace the pause with. */
    private List<Component> holdRows(String product, boolean held, @Nullable Instant heldUntil) {
        List<Component> rows = new ArrayList<>();
        if (!isClickable()) {
            rows.add(setting("Пауза", held ? text(holdText(heldUntil), ChatPalette.WARN) : selected("нет", ChatPalette.OK)));
        } else if (!held) {
            rows.add(setting("Пауза", selected("нет", ChatPalette.OK).append(text(" ")).append(holdPeriods(product, false))));
        } else {
            rows.add(setting("Пауза", selected(holdText(heldUntil), ChatPalette.WARN)).append(text(" "))
                .append(button("[Снять]", getRoutes().set(product, "hold", "off"), "Снова предлагать и загружать обновления", ChatPalette.OK)));
            rows.add(setting("Продлить", holdPeriods(product, true)));
        }
        return rows;
    }

    private Component holdPeriods(String product, boolean replacing) {
        Component line = Component.empty();
        List<String> periods = UpdateCatalogView.HOLD_PERIODS;
        for (int index = 0; index < periods.size(); index++) {
            String period = periods.get(index);
            String hint = replacing ? "Пауза на " + periodLabel(period) + " с этого момента" : "Приостановить обновления на " + periodLabel(period);
            line = line.append(text(index == 0 ? "" : " ").append(option(periodLabel(period), getRoutes().set(product, "hold", period),
                hint + ".\nНовые версии не предлагаются и не загружаются.", ChatPalette.WARN)));
        }
        return line;
    }

    /** {@code 3d} as {@code 3 д}, the way periods read everywhere else. */
    private static String periodLabel(String period) {
        char unit = period.charAt(period.length() - 1);
        return period.substring(0, period.length() - 1) + (unit == 'h' ? " ч" : unit == 'd' ? " д" : " мин");
    }

    /**
     * A row of mutually exclusive choices drawn as buttons, each in its own color: the current one is {@code [✔ name]}
     * in its color, the others have gray brackets and switch to that choice on click.
     */
    private <T> Component choices(String label, List<Pair<T, String>> options, T current, Function<T, TextColor> color,
                                  Function<T, Pair<String, String>> command) {
        List<Component> shown = new ArrayList<>();
        for (Pair<T, String> option : options) {
            T value = option.getFirst();
            String name = option.getSecond();
            if (value.equals(current)) {
                shown.add(selected(name, color.apply(value)));
            } else if (isClickable()) {
                Pair<String, String> action = command.apply(value);
                shown.add(bracketed(name, color.apply(value)).clickEvent(ClickEvent.runCommand(action.getFirst()))
                    .hoverEvent(tooltip(text("Выбрать: " + name, color.apply(value)), text(action.getSecond(), ChatPalette.TEXT),
                        blank(), text(action.getFirst(), ChatPalette.LABEL))));
            } else {
                shown.add(text(name, ChatPalette.FAINT));
            }
        }
        Component line = shown.get(0);
        for (Component option : shown.subList(1, shown.size())) {
            line = line.append(text(" ")).append(option);
        }
        return setting(label, line);
    }
}
