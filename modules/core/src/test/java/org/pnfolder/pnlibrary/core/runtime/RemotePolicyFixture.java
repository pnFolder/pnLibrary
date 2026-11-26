package org.pnfolder.pnlibrary.core.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.context.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.remote.context.ServerInfo;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources;
import org.pnfolder.pnlibrary.core.remote.monitor.RuntimeRemotePolicy;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;

/** A library, a platform and a task scope that record what the library's own remote policy does with them. */
final class RemotePolicyFixture {
    final Object owner = new Object();
    final PnLibrary library = mock(PnLibrary.class, withSettings().extraInterfaces(RuntimeResources.class));
    final PlatformAdapter platform = mock(PlatformAdapter.class);
    final TaskScope tasks = mock(TaskScope.class);
    final PnLogger logger = mock(PnLogger.class);
    final List<Runnable> ready = new ArrayList<>();
    TaskSpec task;
    boolean closed;
    int closes;

    RemotePolicyFixture(PlatformType type) {
        when(library.getOwner()).thenReturn(owner);
        when(library.getVersion()).thenReturn("1.0.0");
        when(((RuntimeResources) library).getRuntimeTasks()).thenReturn(tasks);
        LoggingService logging = mock(LoggingService.class);
        when(library.getLogging()).thenReturn(logging);
        when(logging.logger(owner, "remote-policy")).thenReturn(logger);
        when(platform.getType()).thenReturn(type);
        when(platform.getImplementationName()).thenReturn(type.name());
        Map<String, String> details = new HashMap<>();
        details.put("name", "pnLibrary");
        details.put("authors", "pnFolder");
        when(platform.ownerDetails(owner)).thenReturn(details);
        doAnswer(call -> {
            ready.add(call.getArgument(0));
            return null;
        }).when(platform).whenServerReady(any(Runnable.class));
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(platform).executeGlobal(any(Runnable.class));
        doAnswer(call -> {
            task = call.getArgument(0);
            return mock(TaskHandle.class);
        }).when(tasks).schedule(any(TaskSpec.class));
        RemotePolicyContext context = RemotePolicyContext.builder().product(new ProductInfo("pnlibrary", "pnLibrary", "1.0.0"))
            .platform(new PlatformInfo(type, type.name(), "1.0"))
            .server(new ServerInfo("1.0", MinecraftVersion.parseInfo(null))).build();
        // The platform contract is non-null, so every policy of this test gets a context, whatever its values.
        when(platform.remotePolicyContext(any(), anyMap())).thenReturn(context);
    }

    void start(RemotePolicySettings policy) {
        RuntimeRemotePolicy.start(library, platform, RuntimeRemotePolicy.validated(policy), () -> closed, () -> {
            closes++;
            closed = true;
        });
    }
}
