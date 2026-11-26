package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.util.Objects;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.notice.ChatTone;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleTree;
import org.pnfolder.pnlibrary.core.downloads.DownloadFailureDetails;
import org.pnfolder.pnlibrary.core.downloads.DownloadFailureInterpreter;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata;

/** Reports dependency downloads to the console: failures with advice, staged files and dependencies confirmed after a restart. */
public final class DependencyDownloadReporter {
    private final PlatformLogging logging;
    private final PlatformOwnerMetadata ownership;
    private final String root;

    /** Reports with the control command {@code pn}. */
    public DependencyDownloadReporter(@NotNull PlatformLogging logging, @NotNull PlatformOwnerMetadata ownership) {
        this(logging, ownership, "pn");
    }

    /**
     * Reports with {@code root} as the control command as typed on this platform: {@code pn}, or {@code pnproxy} on a
     * proxy where {@code pn} belongs to the game servers.
     */
    public DependencyDownloadReporter(@NotNull PlatformLogging logging, @NotNull PlatformOwnerMetadata ownership,
                                      @NotNull String root) {
        this.logging = Objects.requireNonNull(logging, "logging");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.root = root;
    }

    /** Prints why a download failed, with advice for the user and the developer. */
    public void failure(@NotNull Object owner, @NotNull Throwable error, @NotNull LogLevel level) {
        DownloadFailureDetails details = DownloadFailureInterpreter.explain(error);
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.FAILURE), "ЗАВИСИМОСТЬ НЕ ПОДГОТОВЛЕНА")
            .mascot("x.x", ownerName(owner), details.getHeadline())
            .blank()
            .tree(ConsoleTree.builder("Причина").child(details.getReason()).build())
            .blank()
            .tree(ConsoleTree.builder("Что может сделать пользователь").child(details.getUserAction()).build())
            .blank()
            .tree(ConsoleTree.builder("Что сообщить разработчику").child(details.getDeveloperAction()).build())
            .blank()
            .status("Файл не установлен")
            .build()
            .send(line -> logging.console(owner, line));
        if (!(error instanceof IllegalArgumentException) && !(error instanceof IOException)) {
            logging.log(owner, level, "Пакет загрузок не подготовлен: " + details.getReason(), error);
        }
    }

    /** Tells the administrator what is missing, where to get it, and how to have it installed. */
    public void missing(@NotNull Object owner, @NotNull MissingGroup group, boolean installingNow) {
        String command = root + " deps install " + group.getCode();
        List<MissingDependency> needed = new ArrayList<>();
        List<MissingDependency> optional = new ArrayList<>();
        for (MissingDependency item : group.getItems()) {
            (item.getRequired() ? needed : optional).add(item);
        }
        ConsoleCard.Builder card = ConsoleCard.builder(ConsoleStyle.theme(ChatTone.WARNING), "НЕ ХВАТАЕТ ПЛАГИНОВ")
            .mascot("o.o", group.getOwner(), "нужны другие плагины")
            .blank();
        addList(card, "Обязательные", needed);
        addList(card, "Необязательные", optional);
        card.tree(nextSteps(group, command, installingNow)).blank()
            .status(needed.isEmpty()
                ? "Без них " + group.getOwner() + " работает, но без части возможностей"
                : "Без них " + group.getOwner() + " работает не полностью")
            .build()
            .send(line -> logging.console(owner, line));
        if (!installingNow && group.getInstallable()) {
            logging.log(owner, LogLevel.WARNING, group.getOwner() + ": не хватает " + names(group)
                + ". Установить: " + command);
        }
    }

    /** Tells that the dependency files are downloaded and the server must be restarted. */
    public void staged(@NotNull Object owner, @NotNull List<FileDownload> declarations) {
        ConsoleTree.Builder files = ConsoleTree.builder("Скачанные зависимости");
        for (FileDownload declaration : declarations) {
            files.child(Paths.get(declaration.getRelativePath()).getFileName().toString());
        }
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.SUCCESS), "ЗАВИСИМОСТИ ПОДГОТОВЛЕНЫ")
            .mascot("^.^", ownerName(owner), "файлы готовы к запуску")
            .blank()
            .tree(files.build())
            .blank()
            .tree(ConsoleTree.builder("Что дальше")
                .child("Перезапусти сервер")
                .child("Плагины загрузятся при следующем запуске")
                .build())
            .blank()
            .status("Загрузка завершена")
            .build()
            .send(line -> logging.console(owner, line));
    }

    /** Tells that the downloaded dependencies were loaded by the restarted server. */
    public void connected(@NotNull Object owner, @NotNull List<DownloadedDependency> dependencies) {
        ConsoleTree.Builder tree = ConsoleTree.builder("Подключённые зависимости");
        for (DownloadedDependency dependency : dependencies) {
            tree.child(dependency.getName() + " " + dependency.getVersion());
        }
        ConsoleCard.builder(ConsoleStyle.theme(ChatTone.SUCCESS), "ЗАВИСИМОСТИ ПОДКЛЮЧЕНЫ")
            .mascot("^.^", ownerName(owner), "сервер успешно перезапущен")
            .blank()
            .tree(tree.build())
            .blank()
            .status("Зависимости загружены и работают")
            .build()
            .send(line -> logging.console(owner, line));
    }

    private void addList(ConsoleCard.Builder card, String name, List<MissingDependency> items) {
        if (items.isEmpty()) {
            return;
        }
        ConsoleTree.Builder tree = ConsoleTree.builder(name);
        for (MissingDependency item : items) {
            describe(tree, item);
        }
        card.tree(tree.build()).blank();
    }

    private ConsoleTree nextSteps(MissingGroup group, String command, boolean installingNow) {
        ConsoleTree.Builder tree = ConsoleTree.builder("Что сделать");
        if (installingNow) {
            tree.child("Скачиваю сам, это займёт несколько секунд; потом перезапустите сервер");
        } else if (group.getInstallable()) {
            tree.child("Вставьте в консоль: " + command).child("Или включите автозагрузку: " + root + " deps auto on");
        } else {
            tree.child("Скачайте по ссылкам выше и положите в папку plugins");
        }
        return tree.build();
    }

    /** One missing plugin: its name and version, why it is needed, how the file is checked, and where to get it. */
    private void describe(ConsoleTree.Builder tree, MissingDependency item) {
        String name = item.getTitle().equalsIgnoreCase(item.getPlugin())
            ? item.getPlugin() : item.getTitle() + " (" + item.getPlugin() + ")";
        tree.child(name + " " + item.getMinimum() + "+");
        if (item.getDescription() != null) {
            tree.child("  " + item.getDescription());
        }
        if (item.getNoFileForServer()) {
            tree.child("  для этого сервера готового файла нет, скачайте вручную");
        }
        if (item.getVariant() != null) {
            tree.child("  выбран файл: " + item.getVariant());
        }
        if (item.getPage() != null) {
            tree.child("  " + item.getPage());
        }
        for (URI source : item.getSources()) {
            tree.child("  " + source);
        }
        if (!item.getSources().isEmpty()) {
            tree.child(item.getChecked()
                ? "  файл проверяется по SHA-256 автора"
                : "  сумма автора не задана, файл берётся с первой доступной ссылки");
        }
    }

    private static String names(MissingGroup group) {
        StringBuilder names = new StringBuilder();
        for (MissingDependency item : group.getItems()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(item.getPlugin());
        }
        return names.toString();
    }

    private String ownerName(Object owner) {
        Map<String, String> details = ownership.ownerDetails(owner);
        String name = details.get("name");
        return name != null ? name : PnLibraryIdentity.NAME;
    }
}
