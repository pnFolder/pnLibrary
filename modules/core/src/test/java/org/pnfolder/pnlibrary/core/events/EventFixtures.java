package org.pnfolder.pnlibrary.core.events;

import java.util.List;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.ResultedEvent;
import org.pnfolder.pnlibrary.api.events.listener.EventHandler;
import org.pnfolder.pnlibrary.api.events.listener.Listener;

/** Events and listeners shared by the event tests. */
final class EventFixtures {
    private EventFixtures() {
    }

    static EventErrorLogger ignoringErrors() {
        return (plugin, message, error) -> { };
    }

    static class TestEvent extends Event {
    }

    static final class ResultEvent extends ResultedEvent<ResultedEvent.ComponentResult> {
        private ResultedEvent.ComponentResult current = ResultedEvent.ComponentResult.allowed();
        String value = "initial";

        @Override
        public ResultedEvent.ComponentResult getResult() {
            return current;
        }

        @Override
        public void setResult(ResultedEvent.ComponentResult result) {
            current = result;
        }
    }

    static final class AnnotatedListener implements Listener {
        private final List<String> calls;

        AnnotatedListener(List<String> calls) {
            this.calls = calls;
        }

        @EventHandler(priority = -250)
        private void early(TestEvent event) {
            calls.add("early");
        }

        @EventHandler
        public void normal(TestEvent event) {
            calls.add("normal");
        }
    }

    static final class InvalidListener implements Listener {
        @EventHandler
        public void invalid() {
        }
    }
}
