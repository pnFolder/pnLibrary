package org.pnfolder.pnlibrary.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;

class ModuleResourceBoundaryTest {
    @Test
    @DisplayName("consumer facade does not expose owner based command or task factories")
    void consumerFacadeDoesNotExposeOwnerBasedFactories() throws Exception {
        Set<String> names = new HashSet<>();
        for (Method method : PnLibrary.class.getMethods()) {
            names.add(method.getName());
        }
        for (String forbidden : new String[] {"getTasks", "getCommands", "runtimeTaskScope", "registerRuntimeCommand"}) {
            assertFalse(names.contains(forbidden), "Public facade must not expose " + forbidden);
        }
        assertEquals(TaskScope.class, ModuleContext.class.getMethod("getTasks").getReturnType());
    }
}
