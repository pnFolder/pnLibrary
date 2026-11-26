package org.pnfolder.pnlibrary.core.remote.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyPolicyImportsTest {
    @Test
    void rewritesNamedImportsToTheirNewPackage() {
        String source = "import org.pnfolder.pnlibrary.api.remote.RemotePolicy;\n"
            + "import org.pnfolder.pnlibrary.api.remote.ServerInfo;\n"
            + "import org.pnfolder.pnlibrary.api.remote.RemotePolicyResult;\n";
        String result = LegacyPolicyImports.rewrite(source);
        assertTrue(result.contains("api.remote.RemotePolicy;"));
        assertTrue(result.contains("api.remote.context.ServerInfo;"));
        assertTrue(result.contains("api.remote.result.RemotePolicyResult;"));
    }

    @Test
    void wildcardImportGetsTheNewPackagesToo() {
        String result = LegacyPolicyImports.rewrite("import org.pnfolder.pnlibrary.api.remote.*;");
        assertTrue(result.contains("api.remote.context.*;") && result.contains("api.remote.result.*;"));
    }

    @Test
    void newStyleSourceIsLeftAlone() {
        String source = "import org.pnfolder.pnlibrary.api.remote.context.ServerInfo;";
        assertEquals(source, LegacyPolicyImports.rewrite(source));
    }
}
