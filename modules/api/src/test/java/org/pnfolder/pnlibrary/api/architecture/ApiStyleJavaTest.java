package org.pnfolder.pnlibrary.api.architecture;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService;
import org.pnfolder.pnlibrary.api.remote.DenyAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

import java.nio.file.Paths;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiStyleJavaTest {
    private static AutoCloseable registerPlaceholder(PlaceholderService service) {
        return service.value("status", viewer -> "online");
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
            .exactArtifact("pnLibrary.jar", 8, null)
            .build();

        FileDownloads downloads = FileDownloads.builder()
            .dataDirectory(Paths.get("plugins", "Example"))
            .file("rules", file -> file
                .url("https://example.invalid/rules.json")
                .destination(org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination.DATA_FOLDER, "rules.json"))
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
