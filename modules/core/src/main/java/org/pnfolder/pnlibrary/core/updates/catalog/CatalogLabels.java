package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.updates.install.ComponentOperation;

/** The words and colors of update states and history operations, shared by every catalog page. */
public final class CatalogLabels {
    /** States with an update on its way: available, downloading or waiting for a restart. */
    public static final Set<UpdateState> PENDING = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)));

    /** States that need the administrator's attention. */
    public static final Set<UpdateState> PROBLEMS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(UpdateState.FAILED, UpdateState.BLOCKED, UpdateState.INCOMPATIBLE)));

    private CatalogLabels() {
    }

    /** Color that marks {@code state} in lists. */
    public static @NotNull TextColor stateColor(@NotNull UpdateState state) {
        switch (state) {
            case UP_TO_DATE:
                return ChatPalette.OK;
            case UPDATE_AVAILABLE:
                return ChatPalette.INFO;
            case UPDATE_STAGED:
            case DOWNLOADING:
            case ROLLED_BACK:
                return ChatPalette.ORANGE;
            case FAILED:
            case BLOCKED:
            case INCOMPATIBLE:
                return ChatPalette.DANGER;
            case FROZEN:
                return ChatPalette.WARN;
            default:
                return ChatPalette.LABEL;
        }
    }

    /** Short user-facing name of {@code state}. */
    public static @NotNull String stateLabel(@NotNull UpdateState state) {
        switch (state) {
            case UP_TO_DATE:
                return "актуально";
            case UPDATE_AVAILABLE:
                return "доступно обновление";
            case UPDATE_STAGED:
                return "подготовлено к перезапуску";
            case FROZEN:
                return "приостановлено";
            case INCOMPATIBLE:
                return "несовместимо";
            case BLOCKED:
                return "заблокировано";
            case CHECKING:
                return "проверка";
            case DOWNLOADING:
                return "загрузка";
            case FAILED:
                return "ошибка";
            default:
                return "откат подготовлен к перезапуску";
        }
    }

    /** {@link #stateLabel} short enough for a catalog row; the full wording stays in the hover card. */
    public static @NotNull String shortStateLabel(@NotNull UpdateState state) {
        switch (state) {
            case UPDATE_STAGED:
                return "ждёт перезапуска";
            case FROZEN:
                return "пауза";
            case ROLLED_BACK:
                return "откат ждёт перезапуска";
            default:
                return stateLabel(state);
        }
    }

    /** User-facing name of a history operation. */
    public static @NotNull String operationLabel(@NotNull String operation) {
        switch (operation) {
            case "CHECK":
                return "проверка";
            case "STAGE":
                return "обновление";
            case "ROLLBACK":
                return "откат";
            case "HEALTH":
                return "проверка запуска";
            case "HEALTH_ROLLBACK":
                return "проверка отката";
            case ComponentOperation.ADDED:
                return "установка";
            case ComponentOperation.REMOVED:
                return "удаление";
            case ComponentOperation.VERSION_CHANGED:
                return "смена версии";
            case ComponentOperation.SETTINGS_CHANGED:
                return "настройки";
            default:
                return "состояние";
        }
    }
}
