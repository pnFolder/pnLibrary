package org.pnfolder.pnlibrary.core.actions;

import java.util.UUID;
import java.util.function.Predicate;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.MockSettings;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer;

/** A player mock that keeps Adventure's default {@code get(Pointer)}, so name and permissions read the sender pointers like in production. */
final class PlayerMocks {
    private PlayerMocks() {
    }

    static OnlinePlayer mockPlayer(String name, UUID id, Predicate<String> permission) {
        MockSettings settings = Mockito.withSettings().defaultAnswer(call -> {
            if (call.getMethod().isDefault()) {
                return call.callRealMethod();
            }
            return Answers.RETURNS_DEFAULTS.answer(call);
        });
        OnlinePlayer player = Mockito.mock(OnlinePlayer.class, settings);
        Mockito.doReturn(id).when(player).getUniqueId();
        Mockito.doReturn(AudienceExtensions.senderPointers(name, id, permission)).when(player).pointers();
        return player;
    }

    static OnlinePlayer mockPlayer(UUID id, Predicate<String> permission) {
        return mockPlayer("Alice", id, permission);
    }

    static OnlinePlayer mockPlayer(Predicate<String> permission) {
        return mockPlayer("Alice", UUID.randomUUID(), permission);
    }
}
