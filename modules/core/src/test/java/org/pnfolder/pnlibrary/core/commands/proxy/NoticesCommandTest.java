package org.pnfolder.pnlibrary.core.commands.proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.commands.engine.CommandRunner;
import org.pnfolder.pnlibrary.core.commands.engine.RecordingSender;
import org.pnfolder.pnlibrary.core.updates.restart.ImportantNotice;

class NoticesCommandTest {
    private static String run(List<ImportantNotice> notices) {
        return CommandRunner.run(new NoticesCommand(() -> notices).definition(), new RecordingSender(), "notices");
    }

    @Test
    void listsEveryNoticeWithItsServerAndAddress() {
        String text = run(Collections.singletonList(new ImportantNotice("lobby", "10.0.0.5:25565", "Нужен перезапуск сервера.")));

        assertTrue(text.contains("Важные объявления"), text);
        assertTrue(text.contains("lobby (10.0.0.5:25565): Нужен перезапуск сервера."), text);
    }

    @Test
    void saysSoWhenThereIsNothingImportant() {
        assertTrue(run(Collections.emptyList()).contains("ничего важного нет"));
    }
}
