package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.result.DenyAction;
import org.pnfolder.pnlibrary.api.remote.result.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.core.remote.report.PolicyDeniedException;
import org.pnfolder.pnlibrary.core.remote.monitor.RuntimeRemotePolicy;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

class RuntimeRemotePolicyTest {
    @TempDir
    Path directory;

    private static RemotePolicySettings copy(RemotePolicySettings policy, String source, PolicyErrorAction onError, DenyAction onDeny) {
        return new RemotePolicySettings(source, policy.getCheckEvery(), onDeny, policy.getValues(), onError, policy.getStartupTimeout());
    }

    private static void runTask(RemotePolicyFixture fixture) {
        fixture.task.getAction().run(mock(TaskContext.class));
    }

    @Test
    void validationPreservesEveryPolicySettingIncludingErrorActions() {
        for (PolicyErrorAction action : PolicyErrorAction.values()) {
            RemotePolicySettings policy = new RemotePolicySettings(directory.resolve("Policy.java").toUri().toString(),
                Duration.ofMinutes(15), DenyAction.DISABLE_PLUGIN, Collections.singletonMap("product", "pnlibrary"), action,
                Duration.ofSeconds(20));

            assertEquals(policy, RuntimeRemotePolicy.validated(policy), "action=" + action);
        }
    }

    @Test
    void libraryPolicyIsCheckedAtStartThenScheduledOnceAfterReadinessWithLibraryOwnedTasks() throws IOException {
        RemotePolicyFixture fixture = new RemotePolicyFixture(PlatformType.BUKKIT);
        RemotePolicySettings policy = policy("AllowPolicy", true);
        fixture.start(policy);
        assertNull(fixture.task);
        fixture.ready.get(0).run();
        fixture.ready.get(0).run();
        assertEquals(policy.getCheckEvery(), fixture.task.getInterval());
        assertEquals(policy.getCheckEvery(), fixture.task.getDelay());
        assertEquals(TaskExecution.Kind.ASYNC, fixture.task.getExecution().getKind());
        verify(fixture.tasks, times(1)).schedule(any(TaskSpec.class));
        verify(fixture.platform, atLeastOnce()).remotePolicyContext(fixture.owner, policy.getValues());
        verify(fixture.library, never()).getPlugins();
    }

    @Test
    void aDenialStopsStartupBeforeThePluginReportsItselfEnabledOnAllThreePlatforms() throws IOException {
        for (PlatformType type : Arrays.asList(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)) {
            RemotePolicyFixture fixture = new RemotePolicyFixture(type);
            RemotePolicySettings denied = policy("Denied" + type.name(), false);
            assertThrows(PolicyDeniedException.class, () -> fixture.start(denied));
            assertTrue(fixture.ready.isEmpty(), type + " schedules nothing after a denial");
            assertNull(fixture.task);
            verify(fixture.platform).disableDependents(fixture.owner);
            verify(fixture.platform).disableOwner(fixture.owner);
        }
    }

    @Test
    void aDenialThatCannotDisableTheLibraryIsLoggedAsCritical() throws IOException {
        RemotePolicyFixture fixture = new RemotePolicyFixture(PlatformType.BUKKIT);
        IllegalStateException failure = new IllegalStateException("plugin manager is gone");
        when(fixture.platform.disableDependents(fixture.owner)).thenThrow(failure);
        when(fixture.platform.disableOwner(fixture.owner)).thenThrow(failure);
        RemotePolicySettings stuck = policy("StuckPolicy", false);

        assertThrows(PolicyDeniedException.class, () -> fixture.start(stuck));

        verify(fixture.logger).error(anyString(), eq(failure));
        verify(fixture.logger).critical(anyString(), eq(failure));
    }

    @Test
    void anAllowedPolicyKeepsThePluginRunningOnAllThreePlatforms() throws IOException {
        for (PlatformType type : Arrays.asList(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.BUNGEECORD)) {
            RemotePolicyFixture fixture = new RemotePolicyFixture(type);
            fixture.start(policy("Allowed" + type.name(), true));
            fixture.ready.get(0).run();
            runTask(fixture);
            assertEquals(0, fixture.closes, String.valueOf(type));
            verify(fixture.platform, never()).disableOwner(fixture.owner);
        }
    }

    @Test
    void aPlatformThatGuardsDependentsSwitchesTheOwnerOffItself() throws IOException {
        RemotePolicyFixture fixture = new RemotePolicyFixture(PlatformType.BUKKIT);
        when(fixture.platform.disableDependents(fixture.owner)).thenReturn(true);
        RemotePolicySettings guarded = policy("GuardedPolicy", false);
        assertThrows(PolicyDeniedException.class, () -> fixture.start(guarded));
        verify(fixture.platform).disableDependents(fixture.owner);
        verify(fixture.platform, never()).disableOwner(fixture.owner);
    }

    @Test
    void aLaterDenialDisablesTheOwnerAndClosesTheRuntimeOnlyWithoutNativeSupport() throws IOException {
        RemotePolicyFixture fixture = new RemotePolicyFixture(PlatformType.BUKKIT);
        Path file = directory.resolve("FlipPolicy.java");
        fixture.start(policy("FlipPolicy", true));
        fixture.ready.get(0).run();
        String flipped = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            .replace("RemotePolicyResult.allow()", "RemotePolicyResult.deny(\"No longer supported\")");
        Files.write(file, flipped.getBytes(StandardCharsets.UTF_8));
        runTask(fixture);
        verify(fixture.platform).disableOwner(fixture.owner);
        assertEquals(1, fixture.closes);
    }

    @Test
    void aPolicyThatCannotBeCheckedKeepsTheLibraryRunningWhenToldTo() {
        RemotePolicySettings missing = RemotePolicySettings.builder().source(directory.resolve("Missing.java").toUri().toString())
            .onError(PolicyErrorAction.KEEP_RUNNING).build();
        RemotePolicyFixture fixture = new RemotePolicyFixture(PlatformType.BUKKIT);
        fixture.start(missing);
        fixture.ready.get(0).run();
        runTask(fixture);
        assertEquals(0, fixture.closes);
        verify(fixture.platform, never()).disableOwner(fixture.owner);
    }

    @Test
    void aPolicyThatCannotBeCheckedAtStartStopsThePluginButAfterOneSuccessAFailureOnlyWarns() throws IOException {
        PolicyErrorAction untilChecked = PolicyErrorAction.DENY_UNTIL_CHECKED;
        RemotePolicySettings missing = RemotePolicySettings.builder().source(directory.resolve("Absent.java").toUri().toString())
            .onError(untilChecked).build();
        assertThrows(PolicyDeniedException.class, () -> new RemotePolicyFixture(PlatformType.BUKKIT).start(missing));

        RemotePolicySettings allowed = policy("LaterPolicy", true);
        RemotePolicySettings checkedOnce = copy(allowed, allowed.getSource(), untilChecked, allowed.getOnDeny());
        RemotePolicyFixture later = new RemotePolicyFixture(PlatformType.BUKKIT);
        later.start(checkedOnce);
        later.ready.get(0).run();
        Files.delete(directory.resolve("LaterPolicy.java"));
        runTask(later);
        assertEquals(0, later.closes);
        verify(later.platform, never()).disableOwner(later.owner);
    }

    @Test
    void unreadablePolicyAndSetupFailuresStopTheLibraryRatherThanIgnoreItsPolicy() throws IOException {
        RemotePolicySettings missing = RemotePolicySettings.builder().source(directory.resolve("Missing.java").toUri().toString()).build();
        assertThrows(PolicyDeniedException.class, () -> new RemotePolicyFixture(PlatformType.VELOCITY).start(missing));

        RemotePolicyFixture unsupported = new RemotePolicyFixture(PlatformType.BUNGEECORD);
        RemotePolicySettings policy = policy("SetupPolicy", true);
        when(unsupported.platform.remotePolicyContext(unsupported.owner, policy.getValues()))
            .thenThrow(new UnsupportedOperationException("not supported"));
        assertThrows(UnsupportedOperationException.class, () -> unsupported.start(policy));
        assertNull(unsupported.task);
    }

    @Test
    void shutdownPreventsPendingStartupAndAlreadyScheduledPolicyCallbacks() throws IOException {
        RemotePolicyFixture beforeReady = new RemotePolicyFixture(PlatformType.BUKKIT);
        beforeReady.start(policy("ClosedPolicy", true));
        beforeReady.closed = true;
        beforeReady.ready.get(0).run();
        assertNull(beforeReady.task);

        RemotePolicyFixture afterReady = new RemotePolicyFixture(PlatformType.VELOCITY);
        RemotePolicySettings denied = policy("LatePolicy", false);
        afterReady.start(copy(denied, policy("LateAllow", true).getSource(), denied.getOnError(), denied.getOnDeny()));
        afterReady.ready.get(0).run();
        afterReady.closed = true;
        runTask(afterReady);
        assertEquals(0, afterReady.closes);
        verify(afterReady.platform, never()).disableOwner(afterReady.owner);
    }

    @Test
    void invalidLibraryDeclarationsFailBeforeBootstrapAndOldStartupSignaturesRemain() throws Exception {
        PlatformAdapter platform = mock(PlatformAdapter.class);
        RemotePolicySettings allowed = policy("ModulePolicy", true);
        RemotePolicySettings modulePolicy = copy(allowed, allowed.getSource(), allowed.getOnError(), DenyAction.DISABLE_MODULE);
        assertThrows(IllegalArgumentException.class, () -> PnLibraryRuntimeHost.start(new Object(), platform, modulePolicy));
        assertThrows(IllegalArgumentException.class,
            () -> RuntimeRemotePolicy.validated(new RemotePolicySettings("http://example.org/Unsafe.java")));
        verifyNoInteractions(platform);
        assertNotNull(PnLibraryRuntimeHost.class.getMethod("start", Object.class, PlatformAdapter.class));
        assertNotNull(PnLibraryRuntimeHost.class.getMethod("startAndRegisterPlatform", Object.class, PlatformAdapter.class, Class.class,
            Object.class));
    }

    private RemotePolicySettings policy(String name, boolean allowed) throws IOException {
        Path source = directory.resolve(name + ".java");
        String result = allowed ? "RemotePolicyResult.allow()" : "RemotePolicyResult.deny(\"Library version no longer supported\")";
        String text = "import org.pnfolder.pnlibrary.api.remote.*;\n"
            + "public final class " + name + " implements RemotePolicy {\n"
            + "    public RemotePolicyResult check(RemotePolicyContext context) {\n"
            + "        if (!context.getProduct().getId().equals(\"pnlibrary\")) throw new IllegalArgumentException(\"Wrong owner\");\n"
            + "        return " + result + ";\n"
            + "    }\n"
            + "}";
        Files.write(source, text.getBytes(StandardCharsets.UTF_8));
        return RemotePolicySettings.builder().source(source.toUri().toString()).checkEvery(Duration.ofMinutes(15))
            .value("product", "pnlibrary").build();
    }
}
