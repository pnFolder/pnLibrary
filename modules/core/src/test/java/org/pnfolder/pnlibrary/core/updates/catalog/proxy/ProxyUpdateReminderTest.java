package org.pnfolder.pnlibrary.core.updates.catalog.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

class ProxyUpdateReminderTest {
    private static List<UpdateSnapshot> one(UpdateState state) {
        return Collections.singletonList(new UpdateSnapshot("shop", "1.0.0", "1.1.0", UpdateChannel.STABLE, state, 1, 1,
            false, null, null));
    }

    private static String text(List<Component> lines) {
        StringBuilder all = new StringBuilder();
        for (Component line : lines) {
            all.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        }
        return all.toString();
    }

    @Test
    void aStagedUpdateEndsWithTheNeedToRestartTheProxy() {
        List<Component> lines = ProxyUpdateReminder.lines(one(UpdateState.UPDATE_STAGED));

        assertTrue(text(lines).contains("Нужен перезапуск прокси"), text(lines));
        assertTrue(text(lines).trim().endsWith("вручную или из панели."), text(lines));
    }

    @Test
    void anAvailableButNotStagedUpdateDoesNotAskForARestart() {
        assertTrue(!text(ProxyUpdateReminder.lines(one(UpdateState.UPDATE_AVAILABLE))).contains("перезапуск прокси"));
        assertEquals(0, ProxyUpdateReminder.lines(one(UpdateState.UP_TO_DATE)).size());
    }
}
