package org.pnfolder.pnlibrary.core.updates.catalog.proxy;

import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatNotice;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults;

/** The chat reminder of proxies: who gets it, how often, and the text. It reads known state and never asks GitHub. */
public final class ProxyUpdateReminder {
    public static final long INTERVAL_SECONDS = UpdateDefaults.REMINDER_INTERVAL.getSeconds();

    private ProxyUpdateReminder() {
    }

    /** Whether the sender may see update reminders. */
    public static boolean canSee(@NotNull Predicate<String> hasPermission) {
        return hasPermission.test("pnlibrary.admin") || hasPermission.test("pnlibrary.updates.notify");
    }

    /** Sends the notice to {@code player} when an update is available or waits for a restart; sends nothing otherwise. */
    public static void send(@NotNull PnLibrary library, @NotNull UUID player) {
        Audience audience = library.getAudiences().player(player);
        if (audience == null) {
            return;
        }
        List<UpdateSnapshot> snapshots = new ArrayList<>();
        for (UpdateRegistration product : library.getUpdates().getProducts().all()) {
            snapshots.add(product.getSnapshot());
        }
        for (Component line : lines(snapshots)) {
            audience.sendMessage(line);
        }
    }

    /**
     * The notice for {@code snapshots}. A proxy never restarts itself, so when an update is staged the notice ends
     * with what the administrator has to do.
     */
    public static @NotNull List<Component> lines(@NotNull List<UpdateSnapshot> snapshots) {
        List<Component> lines = new ArrayList<>(
            UpdateCatalogView.announcement(snapshots, ProxyUpdateCommand.ROUTES, PnLibraryIdentity.SUPPORT_URL));
        boolean staged = false;
        for (UpdateSnapshot snapshot : snapshots) {
            staged |= snapshot.getState() == UpdateState.UPDATE_STAGED;
        }
        if (staged && !lines.isEmpty()) {
            lines.addAll(UpdateCatalogView.notices(Collections.singletonList(new ChatNotice(ChatTone.WARNING,
                "Нужен перезапуск прокси, чтобы подготовленное применилось.",
                "Прокси сам не перезапускается: перезапустите его вручную или из панели.")), ProxyUpdateCommand.ROUTES));
        }
        return lines;
    }
}
