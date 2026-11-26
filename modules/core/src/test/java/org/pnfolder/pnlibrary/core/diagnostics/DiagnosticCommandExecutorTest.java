package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommandEvent;
import org.pnfolder.pnlibrary.core.diagnostics.command.DiagnosticCommandExecutor;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryLauncher;
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskBuilder;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;

class DiagnosticCommandExecutorTest {
    @TempDir
    Path temporary;

    @Test
    void commandFlowCreatesReportAndPublishesCompletion() throws Exception {
        TestPlatform platform = new TestPlatform(temporary);
        PnLibrary library = PnLibraryLauncher.bootstrap(
            new Object(),
            PlatformRuntimeContext.from(platform),
            PnLibraryConfig.builder().upload(false).uploadMode("disabled").cooldown(Duration.ZERO).build());
        final CopyOnWriteArrayList<DiagnosticCommandEvent> events = new CopyOnWriteArrayList<>();
        final CountDownLatch completed = new CountDownLatch(1);

        try {
            new DiagnosticCommandExecutor(library).execute(
                new String[] {"all", "--local"}, false, "console", new Object(), event -> {
                    events.add(event);
                    if (event instanceof DiagnosticCommandEvent.Completed) {
                        completed.countDown();
                    }
                });

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertInstanceOf(DiagnosticCommandEvent.Started.class, events.get(0));
            DiagnosticCommandEvent.Completed result = null;
            for (DiagnosticCommandEvent event : events) {
                if (event instanceof DiagnosticCommandEvent.Completed) {
                    result = (DiagnosticCommandEvent.Completed) event;
                }
            }
            assertTrue(Files.isRegularFile(result.getReport().getLocalFile()));
        } finally {
            library.close();
        }
    }

    private static final class TestPlatform implements PlatformAdapter {
        private final Path dataPath;
        private final PlatformTaskAdapter tasks = new PlatformTaskAdapter() {
            @NotNull
            @Override
            public PlatformTaskBuilder buildTask() {
                return new PlatformTaskBuilder(TestPlatform::dispatch);
            }
        };

        TestPlatform(Path dataPath) {
            this.dataPath = dataPath;
        }

        private static PlatformTaskHandle dispatch(PlatformTaskRequest request) {
            request.getCallback().run();
            return () -> false;
        }

        @NotNull
        @Override
        public Path getDataFolder() {
            return dataPath;
        }

        @NotNull
        @Override
        public PlatformType getType() {
            return PlatformType.BUKKIT;
        }

        @NotNull
        @Override
        public String getId() {
            return "test";
        }

        @NotNull
        @Override
        public PlatformTaskAdapter getTaskAdapter() {
            return tasks;
        }

        @NotNull
        @Override
        public Map<String, String> ownerDetails(@NotNull Object owner) {
            Map<String, String> details = new java.util.LinkedHashMap<String, String>();
            details.put("name", "pnLibrary");
            details.put("version", "2.0.0");
            return details;
        }

        @NotNull
        @Override
        public Map<String, Object> diagnosticDetails(boolean includeSensitive) {
            return Collections.emptyMap();
        }

        @Override
        public void executeGlobal(@NotNull Runnable task) {
            task.run();
        }

        @Override
        public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
            task.run();
        }

        @Override
        public void close() {
        }
    }
}
