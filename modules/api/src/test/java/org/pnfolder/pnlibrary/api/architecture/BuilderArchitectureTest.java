package org.pnfolder.pnlibrary.api.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

class BuilderArchitectureTest {
    @Test
    @DisplayName("public value declarations expose static builder entry points")
    void publicValueDeclarationsExposeStaticBuilderEntryPoints() throws Exception {
        Class<?>[] none = {};
        Class<?>[] name = {String.class};
        Object[][] entryPoints = {
            {CommandDefinition.class, name, "command"},
            {ConfigOptions.class, none, "config options"},
            {DiagnosticConfiguration.class, name, "diagnostic configuration"},
            {DiagnosticContainer.class, name, "diagnostic container"},
            {FileDownloads.class, none, "file downloads"},
            {RemotePolicySettings.class, none, "remote policy"},
            {RemotePolicyContext.class, none, "remote policy context"},
            {TaskQuery.class, none, "task query"},
            {TaskSpec.class, none, "task spec"},
            {PluginUpdateRequest.class, none, "update request"},
            {ExternalPluginDependency.class, new Class<?>[] {String.class, String.class}, "external dependency"},
            {ProductDescriptor.class, none, "product descriptor"},
        };

        for (Object[] entry : entryPoints) {
            Method method = ((Class<?>) entry[0]).getMethod("builder", (Class<?>[]) entry[1]);
            assertTrue(Modifier.isStatic(method.getModifiers()), entry[2] + " builder must be static for Java");
        }
    }
}
