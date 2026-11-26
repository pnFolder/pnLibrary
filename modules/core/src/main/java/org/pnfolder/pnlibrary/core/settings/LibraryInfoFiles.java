package org.pnfolder.pnlibrary.core.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.updates.paths.InfoFileNames;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

/**
 * The three text files an administrator can open in the library's own folder: the help ({@code Помощь.txt}), the
 * overview of every setting with its current value ({@code Настройки.txt}) and the list of every command and permission
 * ({@code Команды.txt}).
 *
 * <p>The text is ours and lives in code. All files are created again at every start and the overview also after every
 * change of a setting, so the links and the values are always those of the running library. Nothing is cached
 * anywhere else, and edits made by hand are lost.
 *
 * <p>A file is rewritten only when its text changed, ignoring the line with the creation time: this way a start that
 * changes nothing leaves the files and their modification times alone. Writing never throws; the first failure is
 * reported once through the warning consumer and the library goes on.
 */
public final class LibraryInfoFiles {
    private final LibraryPaths paths;
    private final LibraryInfoTexts texts;
    private final Consumer<String> warning;
    private final AtomicBoolean warned = new AtomicBoolean(false);

    /** Creates the files for the running version; warnings are dropped. */
    public LibraryInfoFiles(@NotNull LibraryPaths paths, @NotNull Supplier<List<SettingValue>> values,
                            @NotNull String version) {
        this(paths, values, version, Clock.systemUTC(), PnLibraryIdentity.SUPPORT_URL,
            PnLibraryIdentity.REPOSITORY_URL, text -> { });
    }

    /** Creates the files; {@code warning} receives the first failure to write. */
    public LibraryInfoFiles(@NotNull LibraryPaths paths, @NotNull Supplier<List<SettingValue>> values,
                            @NotNull String version, @NotNull Consumer<String> warning) {
        this(paths, values, version, Clock.systemUTC(), PnLibraryIdentity.SUPPORT_URL,
            PnLibraryIdentity.REPOSITORY_URL, warning);
    }

    /** Creates the files with {@code clock} as the source of the creation time. */
    public LibraryInfoFiles(@NotNull LibraryPaths paths, @NotNull Supplier<List<SettingValue>> values,
                            @NotNull String version, @NotNull Clock clock, @NotNull Consumer<String> warning) {
        this(paths, values, version, clock, PnLibraryIdentity.SUPPORT_URL, PnLibraryIdentity.REPOSITORY_URL,
            warning);
    }

    /** Creates the files that point to the given support and repository addresses. */
    public LibraryInfoFiles(@NotNull LibraryPaths paths, @NotNull Supplier<List<SettingValue>> values,
                            @NotNull String version, @NotNull String supportUrl, @NotNull String repositoryUrl) {
        this(paths, values, version, Clock.systemUTC(), supportUrl, repositoryUrl, text -> { });
    }

    /** Creates the files with every part given. */
    public LibraryInfoFiles(@NotNull LibraryPaths paths, @NotNull Supplier<List<SettingValue>> values,
                            @NotNull String version, @NotNull Clock clock, @NotNull String supportUrl,
                            @NotNull String repositoryUrl, @NotNull Consumer<String> warning) {
        this.paths = paths;
        this.texts = new LibraryInfoTexts(paths, values, version, clock, supportUrl, repositoryUrl);
        this.warning = warning;
    }

    /** Writes every file; call it once the settings are loaded. */
    public void refresh() {
        refreshHelp();
        refreshSettings();
        refreshCommands();
    }

    /** Writes the help file. */
    public void refreshHelp() {
        publish(paths.getHelp(), helpText());
    }

    /** Writes the settings overview; call it after a setting changed. */
    public void refreshSettings() {
        publish(paths.getSettingsOverview(), settingsText());
    }

    /** Writes the list of commands and permissions. */
    public void refreshCommands() {
        publish(paths.getCommandGuide(), commandsText());
    }

    /** The text of the help file. */
    public @NotNull String helpText() {
        return texts.help();
    }

    /** The text of the settings overview. */
    public @NotNull String settingsText() {
        return texts.settings();
    }

    /** The text of the command guide: the header, how to read it and {@link LibraryCommandGuide}. */
    public @NotNull String commandsText() {
        return texts.commands();
    }

    /**
     * Writes {@code text} under the Russian name, or under the ASCII one when the server cannot create a file with a
     * Russian name: a JVM that runs with a non-UTF-8 file-name encoding ({@code sun.jnu.encoding}, common on Linux
     * hosts with the C locale) fails with {@link InvalidPathException} or {@link IOException} for such a name.
     */
    private void publish(InfoFileNames names, String text) {
        try {
            try {
                writeIfChanged(paths.getPluginFolder().resolve(names.getPreferred()), text);
            } catch (InvalidPathException | IOException error) {
                writeIfChanged(paths.getPluginFolder().resolve(names.getAscii()), text);
            }
        } catch (Exception error) {
            if (warned.compareAndSet(false, true)) {
                warning.accept("Не удалось записать файл " + names.getPreferred() + " в папку плагина: "
                    + error.getMessage() + ". Работе это не мешает.");
            }
        }
    }

    private void writeIfChanged(Path target, String text) throws IOException {
        String existing = Files.isRegularFile(target) ? readOrNull(target) : null;
        if (existing != null && withoutTime(existing).equals(withoutTime(text))) {
            return;
        }
        AtomicFiles.write(target, text.getBytes(StandardCharsets.UTF_8));
    }

    private static String readOrNull(Path file) {
        try {
            return TextFiles.read(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    private static List<String> withoutTime(String text) {
        List<String> lines = new ArrayList<String>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (!line.startsWith(LibraryInfoTexts.CREATED)) {
                lines.add(line);
            }
        }
        return lines;
    }
}
