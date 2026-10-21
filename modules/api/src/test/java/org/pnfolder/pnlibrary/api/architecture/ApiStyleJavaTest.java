package org.pnfolder.pnlibrary.api.architecture;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.commands.CommandDefinition;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRegistration;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.plugin.DenyAction;
import org.pnfolder.pnlibrary.api.plugin.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.tasks.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest;

import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiStyleJavaTest {
    private static PlaceholderRegistration<String> registerPlaceholder(PlaceholderService service) {
        return service.register("status", String.class, placeholder -> placeholder
            .resolve(request -> "online")
            .access(PlaceholderAccess.ownerOnly())
            .fallback("offline"));
    }

    @Test
    void immutableDeclarationsUseTheSameBuilderShape() {
        TaskSpec task = TaskSpec.builder()
            .name("cleanup")
            .execution(TaskExecution.async())
            .delay(Duration.ofSeconds(1))
            .action(context -> { })
            .build();

        PluginUpdateRequest updates = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnLibrary")
            .automaticDownload(false)
            .exactArtifact("pnLibrary.jar", 8, null)
            .build();

        FileDownloads downloads = FileDownloads.builder()
            .dataDirectory(Paths.get("plugins", "Example"))
            .file("rules", file -> file
                .url("https://example.invalid/rules.json")
                .destination(org.pnfolder.pnlibrary.api.downloads.DownloadDestination.DATA_FOLDER, "rules.json"))
            .build();

        CommandDefinition command = CommandDefinition.builder("example")
            .literal("reload", node -> node.executes(context -> { }))
            .build();

        RemotePolicySettings policy = RemotePolicySettings.builder()
            .source("https://example.invalid/Policy.java")
            .checkEvery(Duration.ofHours(6))
            .onDeny(DenyAction.DISABLE_MODULE)
            .value("environment", "test")
            .build();

        assertEquals("cleanup", task.getName());
        assertEquals("pnFolder", updates.getRepositoryOwner());
        assertTrue(downloads.getFiles().size() == 1);
        assertEquals("reload", command.getRoot().getChildren().get(0).getName());
        assertEquals("test", policy.getValues().get("environment"));
    }
}
