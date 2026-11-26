package org.pnfolder.pnlibrary.core.remote;

import org.pnfolder.pnlibrary.core.remote.engine.PolicyCompilers;
import org.pnfolder.pnlibrary.core.remote.engine.RemotePolicyEngine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.context.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.result.RemotePolicyResult;
import org.pnfolder.pnlibrary.api.remote.context.ServerInfo;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

class RemotePolicyEngineTest {
    private final RemotePolicyEngine engine = new RemotePolicyEngine(new PolicyCompilers());

    @Test
    void compilesARawJavaPolicyAgainstTheSharedContext() throws Exception {
        String source = "package example;\n"
            + "import org.pnfolder.pnlibrary.api.remote.*;\n"
            + "public final class Policy implements RemotePolicy {\n"
            + "    public RemotePolicyResult check(RemotePolicyContext context) {\n"
            + "        return context.getPlatform().getType().name().equals(\"VELOCITY\")\n"
            + "            ? RemotePolicyResult.deny(\"update required\") : RemotePolicyResult.allow();\n"
            + "    }\n"
            + "}";
        RemotePolicyContext context = RemotePolicyContext.builder()
            .product(new ProductInfo("demo", "Demo", "1.0.0"))
            .platform(new PlatformInfo(PlatformType.VELOCITY, "Velocity", "3.3.0"))
            .server(new ServerInfo("3.3.0", MinecraftVersion.parseInfo(null)))
            .build();
        RemotePolicyResult result = engine.checkSource(source.getBytes(StandardCharsets.UTF_8), context);
        assertFalse(result.getAllowed());
        assertEquals("update required", result.getMessage());
    }
}
