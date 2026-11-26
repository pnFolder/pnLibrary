package org.pnfolder.pnlibrary.updates.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.console.ConsoleTheme;

class UpdateAnnouncementRendererTest {
    private static final ConsoleTheme PLAIN = new ConsoleTheme("", "", "", "", "");

    private static boolean any(List<String> lines, java.util.function.Predicate<String> condition) {
        for (String line : lines) {
            if (condition.test(line)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("available update uses the console card design")
    void availableUpdateUsesTheConsoleCardDesign() {
        ProductId product = ProductId.of("pndemo");
        SemanticVersion from = SemanticVersion.parse("2.5.0");
        SemanticVersion to = SemanticVersion.parse("2.6.0-beta.1");
        ArtifactDescriptor artifact = new ArtifactDescriptor("pndemo-2.6.0-beta.1-bukkit-java8.jar", PlatformType.BUKKIT, 8,
            null, null, null, URI.create("https://example.test/pndemo.jar"));
        ProductRelease release = new ProductRelease(product, to, UpdateChannel.BETA, new ApiVersionRange(1, 1), null,
            Collections.emptyList(), null, Collections.singletonList(artifact), Collections.emptyList(), null, null, "");
        UpdatePlan plan = new UpdatePlan(1, Collections.singletonList(new ProductChange(product, from, to)),
            Collections.singletonList(release));
        List<String> lines = UpdateAnnouncementRenderer.render(
            new UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE, plan, Collections.emptyList(), null),
            PLAIN);

        assertTrue(any(lines, line -> line.contains("ПРОВЕРКА ОБНОВЛЕНИЙ")));
        assertTrue(any(lines, line -> line.contains("ОБНОВЛЕНИЯ")));
        assertTrue(any(lines, line -> line.contains("Плагин") && line.contains("pndemo")));
        assertTrue(any(lines, line -> line.contains("2.5.0") && line.contains("2.6.0-beta.1")));
        assertTrue(any(lines, line -> line.contains("тестовый канал Beta")));
        assertTrue(any(lines, line -> line.contains("pndemo-2.6.0-beta.1-bukkit-java8.jar")));
        assertTrue(any(lines, line -> line.contains("Автозагрузка выключена")));
    }

    @Test
    @DisplayName("failed update explains the result without raw logger output")
    void failedUpdateExplainsTheResultWithoutRawLoggerOutput() {
        List<String> lines = UpdateAnnouncementRenderer.render(
            new UpdatePlanSnapshot(UUID.randomUUID(), 2, UpdateState.FAILED, null, Collections.emptyList(), "Read timed out"),
            PLAIN);

        assertTrue(any(lines, line -> line.contains("ПРИЧИНА")));
        assertTrue(any(lines, line -> line.contains("Read timed out")));
        assertTrue(any(lines, line -> line.contains("Проверка не выполнена")));
        assertTrue(!any(lines, line -> line.startsWith("[pnLibrary]")));
    }
}
