package org.pnfolder.pnlibrary.core.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.text.ConsoleStyle;

class ModuleLoggingTest {
    @Test
    void startupBoxUsesTheSharedConsolePalette() {
        TestPlatform platform = new TestPlatform("Demo");
        new ModuleLogging(platform, platform, platform, platform)
            .box(this, "Demo", "1.0.0")
            .ok("Хранилище", "подключено")
            .warn("Кэш", "пуст")
            .show();

        String accent = ConsoleStyle.color(NamedTextColor.YELLOW);
        assertTrue(platform.lines.stream().anyMatch(l -> l.startsWith(accent) && l.contains(ConsoleStyle.BOLD + "PNFOLDER PLUGIN")));
        assertTrue(platform.lines.stream().anyMatch(l -> l.contains("Хранилище")
            && l.endsWith(ConsoleStyle.color(NamedTextColor.GREEN) + ConsoleStyle.BOLD + "[ OK ]")));
        assertTrue(platform.lines.stream().anyMatch(
            l -> l.endsWith(ConsoleStyle.color(NamedTextColor.GOLD) + ConsoleStyle.BOLD + "[ WARN ]")));
        String plain = String.join("\n", platform.lines).replaceAll("§[0-9a-fk-or]", "");
        assertTrue(plain.contains("Demo успешно включён"));
        assertTrue(plain.contains("└ пуст"));
    }
}
