package ru.privatenull.pnlibrary.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleCardTest {
    @Test void rendersStableCardStructure() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "READY")
                .mascot("( ^.^ )", "pnLibrary установлена", "система подключена")
                .blank()
                .detail("Версия", "2.2.0")
                .lastDetail("Состояние", "включена")
                .blank()
                .status("Плагин продолжает запуск")
                .build().render();

        assertEquals("", lines.get(0));
        assertTrue(lines.stream().anyMatch(it -> it.contains("pnLibrary установлена")));
        assertTrue(lines.stream().anyMatch(it -> it.contains("Версия")));
        assertEquals("", lines.get(lines.size() - 1));
    }
}
