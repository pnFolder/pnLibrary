package org.pnfolder.pnlibrary.core.menu.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependency;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;

/** The missing-plugin facts of {@link LocalMenuData}. */
final class LocalDependencyData {
    private final PnLibrary library;

    LocalDependencyData(PnLibrary library) {
        this.library = library;
    }

    Boolean automatic() {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        return downloads == null ? null : downloads.isAutomatic();
    }

    void setAutomatic(boolean value) {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads != null) {
            downloads.setAutomatic(value);
        }
    }

    List<MissingPlugins> missing() {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        List<MissingPlugins> result = new ArrayList<>();
        if (downloads == null) {
            return result;
        }
        for (MissingGroup group : downloads.missingGroups()) {
            List<String> lines = new ArrayList<>();
            Set<String> links = new LinkedHashSet<>();
            for (MissingDependency item : group.getItems()) {
                lines.add(item.getTitle() + " " + item.getMinimum() + "+ · " + (item.getRequired() ? "обязательный" : "необязательный"));
                if (item.getPage() != null) {
                    links.add(item.getPage().toString());
                }
                for (Object source : item.getSources()) {
                    links.add(source.toString());
                }
            }
            result.add(new MissingPlugins(group.getCode(), group.getOwner(), group.getInstallable(), lines, new ArrayList<>(links)));
        }
        return result;
    }

    CompletionStage<String> install(String code) {
        DownloadService downloads = RuntimeBridge.dependencyDownloads(library);
        if (downloads == null) {
            return CompletableFuture.completedFuture("Загрузки зависимостей здесь недоступны.");
        }
        CompletionStage<List<DownloadSnapshot>> stage;
        try {
            stage = downloads.installGroup(code);
        } catch (IllegalArgumentException error) {
            return CompletableFuture.completedFuture(error.getMessage() != null ? error.getMessage() : "Установить нельзя.");
        }
        if (stage == null) {
            return CompletableFuture.completedFuture("Такого кода нет или всё уже скачано.");
        }
        return stage.handle((shots, error) -> {
            if (error != null) {
                return "Не вышло: " + error.getMessage();
            }
            DownloadSnapshot failed = firstFailed(shots);
            if (failed != null) {
                return "Не вышло: " + (failed.getMessage() != null ? failed.getMessage() : "причина неизвестна");
            }
            return "Готово. Перезапустите сервер, чтобы плагины загрузились.";
        });
    }

    private static DownloadSnapshot firstFailed(List<DownloadSnapshot> shots) {
        if (shots != null) {
            for (DownloadSnapshot shot : shots) {
                if (shot.getState() == DownloadState.FAILED) {
                    return shot;
                }
            }
        }
        return null;
    }
}
