package org.pnfolder.pnlibrary.core.updates.catalog;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** Pure, platform-independent wording of the update state used by the update commands. */
public final class UpdateStatusMessages {
    private UpdateStatusMessages() {
    }

    /** Describes the effective plan and product state as short messages. */
    public static @NotNull List<ChatNotice> status(@NotNull UpdatePlanSnapshot plan, @NotNull UpdateSnapshot plugin) {
        boolean isLibrary = plugin.getProduct().equalsIgnoreCase(PnLibraryIdentity.PRODUCT_ID);
        String subject = isLibrary ? "библиотека pnLibrary" : "плагин " + plugin.getProduct();
        String genitive = isLibrary ? "библиотеки pnLibrary" : "плагина " + plugin.getProduct();
        Object change = null;
        String target = null;
        if (plan.getPlan() != null) {
            for (org.pnfolder.pnlibrary.api.updates.release.ProductChange candidate : plan.getPlan().getChanges()) {
                if (candidate.getProduct().getValue().equalsIgnoreCase(plugin.getProduct())) {
                    change = candidate;
                    target = String.valueOf(candidate.getTo());
                    break;
                }
            }
        }
        UpdateState state = stateOf(plan, plugin, change != null);
        List<ChatNotice> notices = new ArrayList<>();
        switch (state) {
            case FROZEN:
                notices.add(new ChatNotice(ChatTone.WARNING, "Обновления " + genitive + " временно приостановлены."));
                break;
            case BLOCKED:
                notices.add(new ChatNotice(ChatTone.FAILURE, "Обновление " + genitive + " заблокировано."));
                for (BlockedReason reason : plan.getBlockers()) {
                    notices.add(new ChatNotice(ChatTone.WARNING, "Причина: " + blocker(reason)));
                }
                break;
            case FAILED:
                String message = plan.getMessage() != null ? plan.getMessage() : plugin.getMessage() != null ? plugin.getMessage() : "неизвестная ошибка";
                notices.add(new ChatNotice(ChatTone.FAILURE, "Не удалось проверить обновление " + genitive + ".", sentence(message)));
                break;
            case UPDATE_AVAILABLE:
                String latest = plugin.getLatestVersion() != null ? plugin.getLatestVersion() : target != null ? target : "?";
                notices.add(new ChatNotice(ChatTone.INFO,
                    "Для " + genitive + " доступно обновление: v" + plugin.getCurrentVersion() + " » v" + latest + "."));
                break;
            case UPDATE_STAGED:
                notices.add(new ChatNotice(ChatTone.SUCCESS, "Обновление " + genitive + " проверено и подготовлено.",
                    "Полностью перезапустите сервер."));
                break;
            case ROLLED_BACK:
                notices.add(new ChatNotice(ChatTone.SUCCESS, "Предыдущая версия " + genitive + " подготовлена.",
                    "Полностью перезапустите сервер."));
                break;
            default:
                notices.add(new ChatNotice(ChatTone.SUCCESS,
                    Character.toUpperCase(subject.charAt(0)) + subject.substring(1) + " уже использует актуальную версию."));
                break;
        }
        return notices;
    }

    private static UpdateState stateOf(UpdatePlanSnapshot plan, UpdateSnapshot plugin, boolean changed) {
        if (plugin.getState() == UpdateState.FROZEN) {
            return UpdateState.FROZEN;
        }
        UpdateState planned = plan.getState();
        if (planned == UpdateState.BLOCKED || planned == UpdateState.FAILED || planned == UpdateState.ROLLED_BACK) {
            return planned;
        }
        if (changed && planned == UpdateState.UPDATE_STAGED) {
            return UpdateState.UPDATE_STAGED;
        }
        if (changed && (plugin.getState() == UpdateState.UPDATE_AVAILABLE || planned == UpdateState.UPDATE_AVAILABLE)) {
            return UpdateState.UPDATE_AVAILABLE;
        }
        return UpdateState.UP_TO_DATE;
    }

    /** Explains the compatibility or policy reason blocking an update. */
    public static @NotNull String blocker(@NotNull BlockedReason reason) {
        if (reason instanceof BlockedReason.ApiMismatch) {
            BlockedReason.ApiMismatch mismatch = (BlockedReason.ApiMismatch) reason;
            return "плагин " + mismatch.getProduct() + " поддерживает pnLibrary API " + mismatch.getSupportedApi().getMinimum()
                + "–" + mismatch.getSupportedApi().getMaximum() + ", требуется API " + mismatch.getRequiredApi() + "."
                + (mismatch.getRequiredApi() < mismatch.getSupportedApi().getMinimum() ? " Сначала обновите библиотеку pnLibrary." : "");
        }
        if (reason instanceof BlockedReason.MissingDependency) {
            BlockedReason.MissingDependency missing = (BlockedReason.MissingDependency) reason;
            return "для плагина " + missing.getProduct() + " требуется " + missing.getDependency() + " версии "
                + missing.getMinimumVersion() + " или новее.";
        }
        if (reason instanceof BlockedReason.MissingExternalPluginDependency) {
            BlockedReason.MissingExternalPluginDependency missing = (BlockedReason.MissingExternalPluginDependency) reason;
            return "для плагина " + missing.getProduct() + " требуется внешний плагин " + missing.getPlugin() + " версии "
                + missing.getMinimumVersion() + " или новее.";
        }
        if (reason instanceof BlockedReason.Frozen) {
            return "обновления плагина " + ((BlockedReason.Frozen) reason).getProduct() + " временно приостановлены.";
        }
        if (reason instanceof BlockedReason.NoCompatibleRelease) {
            BlockedReason.NoCompatibleRelease none = (BlockedReason.NoCompatibleRelease) reason;
            return "для плагина " + none.getProduct() + " нет версии, совместимой с pnLibrary API " + none.getRequiredApi() + ".";
        }
        return "обновление заблокировано: " + reason + ".";
    }

    /** Ensures diagnostic messages end with exactly one period. */
    private static String sentence(String value) {
        String trimmed = value.trim();
        return (trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed) + ".";
    }
}
