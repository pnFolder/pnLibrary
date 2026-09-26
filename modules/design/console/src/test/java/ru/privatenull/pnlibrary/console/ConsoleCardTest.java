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

    @Test
    void rendersNestedTreeWithContinuousBranches() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "POLICY")
            .tree(ConsoleTree.builder("Версия не поддерживается")
                .branch("Сравнение", versions -> versions
                    .child("Установлена: 2.2.0")
                    .child("Минимальная: 2.3.0"))
                .branch("Что делать", action -> action
                    .branch("После замены", restart -> restart
                        .child("Остановить сервер")
                        .child("Запустить сервер")))
                .build())
            .build().render();

        assertTrue(lines.stream().anyMatch(line -> line.contains("◆ Версия не поддерживается")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("├ Сравнение")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("│ └ Минимальная: 2.3.0")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("└ Что делать")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("└ После замены")));
    }

    @Test
    void mascotOwnsFaceParentheses() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "POLICY")
            .mascot("x.x", "Plugin", "stopped")
            .build().render();

        assertTrue(lines.stream().anyMatch(line -> line.contains("( x.x )")));
    }
}
