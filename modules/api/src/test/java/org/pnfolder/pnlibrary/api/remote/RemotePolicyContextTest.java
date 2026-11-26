package org.pnfolder.pnlibrary.api.remote;

import org.pnfolder.pnlibrary.api.remote.context.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.context.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.context.ServerInfo;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

class RemotePolicyContextTest {
    private static RemotePolicyContext.Builder base() {
        return RemotePolicyContext.builder()
            .product(new ProductInfo("demo", "Demo", "1.0.0"))
            .platform(new PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21.4"))
            .server(new ServerInfo("1.21.4", MinecraftVersion.parseInfo("1.21.4")));
    }

    @Test
    void platformMatchingIgnoresCaseAndPunctuation() {
        PlatformInfo platform = new PlatformInfo(PlatformType.BUKKIT, "Purpur-Server", "1.21.4");

        assertEquals("purpurserver", platform.getKey());
        assertTrue(platform.isNamed("PURPUR server"));
    }

    @Test
    void nativePlatformAccessIsTyped() {
        Object nativeHandle = new Object();
        RemotePolicyContext context = base().nativeHandle(nativeHandle).build();

        assertSame(nativeHandle, context.requireNative(Object.class));
    }

    @Test
    void builderRejectsBlankCustomKeys() {
        assertThrows(IllegalArgumentException.class, () -> RemotePolicyContext.builder().value(" ", "invalid"));
    }

    @Test
    void builderTrimsKeysAndSnapshotsCustomValues() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put(" environment ", "production");
        RemotePolicyContext context = base().values(source).build();

        source.clear();
        assertEquals("production", context.getValues().get("environment"));
        assertThrows(UnsupportedOperationException.class, () -> context.getValues().put("another", "value"));
    }
}
