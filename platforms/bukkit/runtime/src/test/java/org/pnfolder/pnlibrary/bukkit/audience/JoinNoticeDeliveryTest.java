package org.pnfolder.pnlibrary.bukkit.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class JoinNoticeDeliveryTest {
    private final List<TaskSpec> specs = new ArrayList<>();
    private final TaskScope scope = mock(TaskScope.class, (Answer<Object>) call -> {
        if (!call.getMethod().getName().equals("schedule")) {
            return null;
        }
        specs.add((TaskSpec) call.getArguments()[0]);
        return mock(TaskHandle.class);
    });
    private final Player player = mock(Player.class);
    private final TaskContext context = mock(TaskContext.class);

    JoinNoticeDeliveryTest() {
        when(player.isOnline()).thenReturn(true);
    }

    @Test
    void theJournalIsReadByTheAsynchronousTaskAndOnlyTheSendRunsOnThePlayerThread() throws Exception {
        AtomicReference<Thread> builtOn = new AtomicReference<>();
        AtomicReference<Thread> sentOn = new AtomicReference<>();
        List<Component> sent = new ArrayList<>();
        List<Component> lines = Arrays.asList(Component.text("one"), Component.text("two"));
        Thread main = Thread.currentThread();

        new JoinNoticeDelivery(scope).deliver(player, () -> {
            builtOn.set(Thread.currentThread());
            return lines;
        }, text -> {
            sentOn.set(Thread.currentThread());
            sent.addAll(text);
        });

        assertEquals(1, specs.size());
        assertEquals(TaskExecution.Kind.ASYNC, specs.get(0).getExecution().getKind());
        assertNull(builtOn.get(), "nothing is read while the join event is handled");

        Thread worker = new Thread(() -> specs.get(0).getAction().run(context));
        worker.start();
        worker.join();
        assertNotEquals(main, builtOn.get());
        assertEquals(worker, builtOn.get());
        assertEquals(2, specs.size());
        assertEquals(TaskExecution.Kind.ENTITY, specs.get(1).getExecution().getKind());
        assertNull(sentOn.get());

        specs.get(1).getAction().run(context);
        assertEquals(main, sentOn.get());
        assertEquals(lines, sent);
    }

    @Test
    void anEmptyNoticeSchedulesNoSend() {
        new JoinNoticeDelivery(scope).deliver(player, Collections::<Component>emptyList, text -> {
            throw new IllegalStateException("must not send");
        });

        specs.get(0).getAction().run(context);

        assertTrue(specs.size() == 1);
    }
}
