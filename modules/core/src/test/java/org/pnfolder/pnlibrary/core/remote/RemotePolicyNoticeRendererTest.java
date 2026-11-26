package org.pnfolder.pnlibrary.core.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.DenyAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

class RemotePolicyNoticeRendererTest {
    private static PlatformAdapter platform(List<String> lines, boolean full) {
        return (PlatformAdapter) Proxy.newProxyInstance(RemotePolicyNoticeRendererTest.class.getClassLoader(),
            new Class<?>[] {PlatformAdapter.class}, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "console":
                        lines.add((String) arguments[1]);
                        return null;
                    case "getType":
                        return full ? PlatformType.BUKKIT : null;
                    case "details":
                        return full ? Collections.emptyMap() : null;
                    default:
                        return null;
                }
            });
    }

    @Test
    void rendersADeniedPolicyWithItsCompleteExplanationTree() {
        List<String> lines = new ArrayList<String>();
        PluginMetadata metadata = new PluginMetadata(ModuleId.of("demo"), "Demo", "2.0.0", "pnFolder",
            PlatformType.BUKKIT, "Paper", "25", 25);
        RemotePolicySettings policy = RemotePolicySettings.builder()
            .source("https://example.org/Policy.java")
            .onDeny(DenyAction.DISABLE_MODULE)
            .build();
        RemotePolicyExplanation explanation = RemotePolicyExplanation.builder("Требуется обновление")
            .child(RemotePolicyExplanation.builder("Версия устарела").child("Установлена 2.0.0").build())
            .build();

        new RemotePolicyNoticeRenderer(platform(lines, true)).render(new Object(), metadata, policy, false, explanation);

        assertTrue(any(lines, line -> line.contains("ПРОВЕРКА СОВМЕСТИМОСТИ")));
        assertTrue(any(lines, line -> line.contains("Требуется обновление")));
        assertTrue(any(lines, line -> line.contains("Установлена 2.0.0")));
        assertTrue(any(lines, line -> line.startsWith(ConsoleStyle.color(ChatPalette.DANGER))
            && line.contains("Модуль безопасно остановлен")));
        assertTrue(any(lines, line -> line.startsWith(ConsoleStyle.THEME.border)
            && line.contains("ПРОВЕРКА СОВМЕСТИМОСТИ")));
    }

    @Test
    void aDecisionCardShowsItsOwnStatusHeadlineDetailsStepsAndLinks() {
        List<String> lines = new ArrayList<String>();
        PluginMetadata metadata = new PluginMetadata(ModuleId.of("demo"), "Demo", "0.0.1", "pnFolder",
            PlatformType.BUKKIT, "Paper", "25", 25);
        RemotePolicySettings policy = RemotePolicySettings.builder().source("https://example.org/Policy.java").build();
        RemotePolicyResult result = RemotePolicyResult.denying("Версия устарела")
            .status("устарела").headline("нужно обновление")
            .detail("Минимальная", "0.0.2")
            .step("Скачайте новую версию")
            .link("Скачать", "https://example.org/download")
            .build();

        new RemotePolicyNoticeRenderer(platform(lines, false)).render(new Object(), metadata, policy, result);

        String text = String.join(" | ", lines);
        assertTrue(text.contains("устарела"), text);
        assertTrue(text.contains("нужно обновление"), text);
        assertTrue(text.contains("Минимальная") && text.contains("0.0.2"), text);
        assertTrue(text.contains("Скачайте новую версию"), text);
        assertTrue(text.contains("Скачать: https://example.org/download"), text);
        assertFalse(text.contains("не поддерживается"), text);
    }

    private interface LinePredicate {
        boolean test(String line);
    }

    private static boolean any(List<String> lines, LinePredicate predicate) {
        for (String line : lines) {
            if (predicate.test(line)) {
                return true;
            }
        }
        return false;
    }
}
