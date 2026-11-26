package org.pnfolder.pnlibrary.core.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.ServerInfo;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

class PolicyCompilersTest {
    private final RemotePolicyContext context = RemotePolicyContext.builder()
        .product(new ProductInfo("demo", "Demo", "1.0.0"))
        .platform(new PlatformInfo(PlatformType.BUKKIT, "Paper", "1.21"))
        .server(new ServerInfo("1.21", MinecraftVersion.parseInfo(null)))
        .build();

    private final byte[] source = ("package example;\n"
        + "import org.pnfolder.pnlibrary.api.remote.*;\n"
        + "public final class EcjPolicy implements RemotePolicy {\n"
        + "    public RemotePolicyResult check(RemotePolicyContext context) {\n"
        + "        return RemotePolicyResult.deny(\"denied by ecj\");\n"
        + "    }\n"
        + "}").getBytes(StandardCharsets.UTF_8);

    @Test
    void ecjCompilesAPolicyWhenTheServerHasNoJavac() throws Exception {
        RemotePolicyEngine engine = new RemotePolicyEngine(
            new PolicyCompilers(() -> null, PolicyCompilers.class.getClassLoader()));
        assertEquals("denied by ecj", engine.checkSource(source, context).getMessage());
    }

    @Test
    void aJreWhereEcjCannotBeLoadedIsReportedAsAMissingCompiler() throws Exception {
        Path root = Files.createTempDirectory("pn-no-ecj");
        ClassLoader bare = ClassLoader.getSystemClassLoader().getParent();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> new PolicyCompilers(() -> null, bare).compile(root.resolve("A.java"), root, ""));
        assertTrue(RemotePolicyFailureInterpreter.isCompilerMissing(failure));
    }

    @Test
    void aBrokenPolicyReportsItsCompileErrors() {
        RemotePolicyEngine engine = new RemotePolicyEngine(
            new PolicyCompilers(() -> null, PolicyCompilers.class.getClassLoader()));
        byte[] broken = "public class Broken implements org.pnfolder.pnlibrary.api.remote.RemotePolicy { int x = ; }"
            .getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> engine.checkSource(broken, context));
        assertTrue(String.valueOf(failure.getMessage()).startsWith("Remote policy compilation failed"));
    }
}
