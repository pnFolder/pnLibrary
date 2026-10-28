package org.pnfolder.pnlibrary.loader;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.console.ConsoleCard;
import org.pnfolder.pnlibrary.console.ConsoleSink;
import org.pnfolder.pnlibrary.console.ConsoleTheme;
import org.pnfolder.pnlibrary.console.ReportedFailure;

/**
 * The console card shown when pnLibrary cannot start: what went wrong, what to do about it and where to ask.
 *
 * <p>The same card is used on every platform, for a library that could not be downloaded and for any other startup
 * error, so an administrator always sees a reason and a way to get help.
 */
public final class StartupFailure {
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private StartupFailure() {
    }

    /**
     * Builds the card for {@code error}.
     *
     * @param theme console colours and symbols
     * @param settings library settings, to name the repositories and folder in the advice
     * @param dataFolder the plugin's own data folder
     * @param error what stopped startup
     * @return the finished card
     */
    public static ConsoleCard card(ConsoleTheme theme, LibrarySettings settings, Path dataFolder, Throwable error) {
        Throwable cause = unwrap(error);
        ConsoleCard.Builder card = ConsoleCard.builder(theme, "ОШИБКА ЗАПУСКА")
                .mascot("( x.x )", ProductInfo.NAME + " не удалось запустить", "плагин остановлен")
                .blank()
                .section("Причина");
        if (cause instanceof LibraryLoadException) {
            LibraryLoadException failure = (LibraryLoadException) cause;
            card.item("Не удалось скачать библиотеку " + failure.library())
                    .lastItem(describe(failure.getCause(), "попыток: " + failure.attempts()));
            card.section("Что делать")
                    .item("Проверьте, что сервер выходит в интернет и открывает: " + repositories(settings))
                    .item("Или положите jar этой библиотеки в папку: " + settings.directoryFor(dataFolder))
                    .item("Или установите полную сборку (universal-full), она ничего не скачивает")
                    .lastItem(ProductInfo.RELEASES_URL);
        } else {
            card.lastItem(describe(cause, null));
            card.section("Что делать")
                    .item("Перезапустите сервер: разовый сбой часто проходит")
                    .item("Если не прошло, приложите к обращению файл logs/latest.log целиком")
                    .lastItem("Последняя версия: " + ProductInfo.RELEASES_URL);
        }
        return card.section("Что будет дальше")
                .item("Плагины, которым нужна " + ProductInfo.NAME + ", тоже не запустятся")
                .item("Их ошибки вроде NoClassDefFoundError: PnLibraryProvider вторичны, чините только эту")
                .lastItem("После исправления перезапустите сервер")
                .section("Не получается?")
                .lastItem("Напишите в поддержку: " + ProductInfo.SUPPORT_URL)
                .blank()
                .status(ProductInfo.NAME + " не был запущен")
                .build();
    }

    /**
     * Whether {@code error} was already explained in the console, so no second card is wanted.
     *
     * @param error what stopped startup
     * @return {@code true} if a card was already shown for it
     */
    public static boolean isReported(Throwable error) {
        return unwrap(error) instanceof ReportedFailure;
    }

    /**
     * Builds the card and prints it line by line, unless the error was already explained.
     *
     * @param theme console colours and symbols
     * @param sink where the lines go
     * @param settings library settings, to name the repositories and folder in the advice
     * @param dataFolder the plugin's own data folder
     * @param error what stopped startup
     */
    public static void report(ConsoleTheme theme, ConsoleSink sink, LibrarySettings settings, Path dataFolder,
                              Throwable error) {
        if (isReported(error)) {
            return;
        }
        card(theme, settings, dataFolder, error).send(sink);
    }

    private static String repositories(LibrarySettings settings) {
        List<String> all = new ArrayList<>(settings.repositories());
        if (settings.mavenCentral()) {
            all.add(MAVEN_CENTRAL);
        }
        all.addAll(settings.fallbacks());
        StringBuilder text = new StringBuilder();
        for (String repository : all) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(repository);
        }
        return text.toString();
    }

    private static String describe(Throwable error, String extra) {
        String message = error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? (error == null ? "причина не указана" : error.getClass().getSimpleName())
                : error.getClass().getSimpleName() + ": " + error.getMessage();
        return extra == null ? message : message + " (" + extra + ")";
    }

    /** Reflection wraps the real failure; show that one. */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
