package org.pnfolder.pnlibrary.core.commands.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependency;
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources;

class DepsCommandTest {
    private final DownloadService downloads = mock(DownloadService.class);
    private final PnLibrary library = mock(PnLibrary.class, withSettings().extraInterfaces(RuntimeResources.class));
    private final RecordingSender sender = new RecordingSender();
    private final CommandDefinition definition = new DepsCommand(library).definition();

    DepsCommandTest() {
        when(((RuntimeResources) library).dependencyDownloads()).thenReturn(downloads);
    }

    private String run(String... words) {
        return CommandRunner.run(definition, sender, "deps", words);
    }

    private MissingGroup group(boolean registered) throws URISyntaxException {
        MissingDependency vault = new MissingDependency("Vault", "1.7.0", new URI("https://example.org/vault"),
                Arrays.asList(new URI("https://example.org/Vault.jar"))).withTitle("Экономика").withDescription("Нужна для денег.")
                .withRequired(true);
        MissingDependency optional = new MissingDependency("Optional", "2.0.0", null, Collections.emptyList()).withRequired(false);
        return new MissingGroup("Shop", "a1b2c3", Arrays.asList(vault, optional), registered ? mock(DownloadRegistration.class) : null);
    }

    @Test
    @DisplayName("nothing missing is said plainly")
    void nothingMissingIsSaidPlainly() {
        when(downloads.missingGroups()).thenReturn(Collections.emptyList());

        String text = run();

        assertTrue(text.contains("Всё нужное установлено"), text);
    }

    @Test
    @DisplayName("a missing group shows title, why, required or not, links and the install command")
    void aMissingGroupShowsTitleWhyRequiredOrNotLinksAndTheInstallCommand() throws URISyntaxException {
        when(downloads.missingGroups()).thenReturn(Arrays.asList(group(true)));

        String text = run();

        assertTrue(text.contains("Shop просит"), text);
        assertTrue(text.contains("Экономика (Vault) 1.7.0+ | обязательный"), text);
        assertTrue(text.contains("Нужна для денег."), text);
        assertTrue(text.contains("https://example.org/Vault.jar"), text);
        assertTrue(text.contains("Optional 2.0.0+ | необязательный"), text);
        assertTrue(text.contains("/pn deps install a1b2c3"), text);
    }

    @Test
    @DisplayName("a group without a direct link tells to download by hand")
    void aGroupWithoutADirectLinkTellsToDownloadByHand() throws URISyntaxException {
        when(downloads.missingGroups()).thenReturn(Arrays.asList(group(false)));

        assertTrue(run().contains("Скачайте по ссылкам"));
    }

    @Test
    @DisplayName("an unknown code is refused")
    void anUnknownCodeIsRefused() {
        when(downloads.installGroup("zzz")).thenReturn(null);

        assertTrue(run("install", "zzz").contains("Такого кода нет"));
    }

    @Test
    @DisplayName("the command is named deps and has install but no switch for the automatic download")
    void theCommandIsNamedDepsAndHasInstallButNoSwitchForTheAutomaticDownload() {
        assertEquals("deps", definition.getName());
        List<String> names = new java.util.ArrayList<String>();
        for (CommandNode child : definition.getRoot().getChildren()) {
            names.add(child.getName());
        }
        assertTrue(names.contains("install"));
        assertFalse(names.contains("auto"), names.toString());
    }
}
