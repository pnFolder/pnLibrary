package org.pnfolder.pnlibrary.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleCardTest {
    @Test
    void rendersStableCardStructure() {
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

        assertTrue(lines.stream().anyMatch(line -> line.contains("┌ Версия не поддерживается")));
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

    @Test
    void longDetailLabelsStaySeparatedFromTheirValues() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "STATUS")
            .firstDetail("Выбранный канал", "стабильный канал")
            .lastDetail("Установленная версия", "2.2.0")
            .build().render();

        assertTrue(lines.stream().anyMatch(line -> line.matches(".*Выбранный канал\\s+стабильный канал.*")));
        assertTrue(lines.stream().anyMatch(line -> line.matches(".*Установленная версия\\s+2.2.0.*")));
    }

    @Test
    void connectorsFollowThePositionInTheGroupWhateverMethodAddedTheRow() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "STATUS")
            .detail("Первая", "1").detail("Вторая", "2").lastDetail("Третья", "3")
            .blank()
            .section("Причина").item("Один").lastItem("Два")
            .blank()
            .detail("Одна", "1")
            .build().render();

        assertTrue(lines.stream().anyMatch(line -> line.contains("┌ Первая")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("├ Вторая")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("└ Третья")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("├ Один")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("└ Два")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("└ Одна")), "a single row closes its own group");
    }

    @Test
    void aTreeWithoutChildrenIsNotDrawnAsTheTopOfAnOpenBranch() {
        List<String> lines = ConsoleCard.builder(ConsoleTheme.plain(), "POLICY")
            .tree(ConsoleTree.builder("Проверка пройдена").build())
            .build().render();

        assertTrue(lines.stream().anyMatch(line -> line.contains("└ Проверка пройдена")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("┌ Проверка пройдена")));
    }
}
