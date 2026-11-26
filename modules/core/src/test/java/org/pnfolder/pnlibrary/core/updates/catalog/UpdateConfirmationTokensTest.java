package org.pnfolder.pnlibrary.core.updates.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UpdateConfirmationTokensTest {
    @Test
    void tokenIsPlayerPlanRevisionActionExpiryAndSingleUseBound() {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        UpdateConfirmationTokens tokens = new UpdateConfirmationTokens(new SecureRandom(), now::get);
        UUID player = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        String token = tokens.issue(player, plan, 7, UpdateAction.PLAN_ROLLBACK, Duration.ofSeconds(30));

        assertTrue(token.length() >= 43);
        assertFalse(tokens.consume(token, UUID.randomUUID(), plan, 7, UpdateAction.PLAN_ROLLBACK));
        assertFalse(tokens.consume(token, player, plan, 8, UpdateAction.PLAN_ROLLBACK));
        assertFalse(tokens.consume(token, player, plan, 7, UpdateAction.RESTART));
        assertFalse(tokens.consume(token, player, plan, 7, UpdateAction.ROLLBACK));
        assertTrue(tokens.consume(token, player, plan, 7, UpdateAction.PLAN_ROLLBACK));
        assertFalse(tokens.consume(token, player, plan, 7, UpdateAction.PLAN_ROLLBACK));

        String expired = tokens.issue(player, plan, 7, UpdateAction.RESTART, Duration.ofSeconds(1));
        now.set(now.get().plusSeconds(2));
        assertFalse(tokens.consume(expired, player, plan, 7, UpdateAction.RESTART));
        assertEquals(0, tokens.cleanup());
    }
}
