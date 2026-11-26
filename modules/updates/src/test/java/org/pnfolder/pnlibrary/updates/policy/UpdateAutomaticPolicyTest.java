package org.pnfolder.pnlibrary.updates.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.BlockedReason;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;
import org.pnfolder.pnlibrary.updates.service.RegisteredUpdate;

class UpdateAutomaticPolicyTest {
    @TempDir
    Path directory;

    private UpdatePolicyOverrides overrides() {
        return new UpdatePolicyOverrides(new UpdatePreferencesStore(directory.resolve("preferences.json")));
    }

    private static UpdateAutomaticPolicy policy(UpdateConfiguration configuration) {
        return new UpdateAutomaticPolicy(configuration, Collections::<RegisteredUpdate>emptyList);
    }

    private static UpdateAutomaticPolicy policy(UpdateConfiguration configuration, UpdatePolicyOverrides overrides) {
        return new UpdateAutomaticPolicy(configuration, overrides, Collections::<RegisteredUpdate>emptyList);
    }

    @Test
    void pluginAutomaticDownloadDoesNotOptTheLibraryIntoAutomaticReplacement() {
        UpdateConfiguration config = new UpdateConfiguration()
            .withLibrary(new UpdateConfiguration.LibraryPolicy(UpdateChannel.DEV, false))
            .withPluginAutomaticDownload(true);
        assertFalse(policy(config).allows(plan("pnlibrary")));
        UpdateConfiguration enabled = config.withLibrary(
            new UpdateConfiguration.LibraryPolicy(UpdateChannel.DEV, true));
        assertTrue(policy(enabled).allows(plan("pnlibrary")));
    }

    @Test
    void everyMemberOfAnAtomicPlanNeedsAutomaticPermission() {
        UpdateConfiguration config = new UpdateConfiguration().withPluginAutomaticDownload(false);
        UpdatePlanSnapshot combined = plan("pnlibrary", "managed-module");
        assertFalse(policy(config).allows(combined));
        assertTrue(policy(config.withPluginAutomaticDownload(true)).allows(combined));
    }

    @Test
    void pluginsUpdateAutomaticallyUnlessAnAdministratorSwitchedItOff() {
        assertTrue(policy(new UpdateConfiguration()).allows(plan("managed-module")));
    }

    @Test
    void anExplicitlyDisabledProductCannotInheritAGlobalAutomaticPermission() {
        UpdatePolicyOverrides overrides = overrides();
        overrides.setAutomatic("managed-module", false);
        assertFalse(policy(new UpdateConfiguration().withPluginAutomaticDownload(true), overrides)
            .allows(plan("managed-module")));
    }

    @Test
    void switchingAllPluginsOffDropsSingleChoicesAndKeepsTheLibraryChoice() {
        UpdatePolicyOverrides overrides = overrides();
        overrides.setAutomatic("managed-module", true);
        overrides.setAutomatic("pnlibrary", true);
        overrides.setAllPlugins(false, "pnlibrary");
        UpdateAutomaticPolicy policy = policy(new UpdateConfiguration(), overrides);
        assertFalse(policy.allows(plan("managed-module")));
        assertTrue(policy.allows(plan("pnlibrary")));
        assertEquals(Boolean.FALSE, overrides.allPlugins());
    }

    private static UpdatePlanSnapshot plan(String... products) {
        List<ProductChange> changes = new ArrayList<ProductChange>();
        for (String product : products) {
            changes.add(new ProductChange(ProductId.of(product), SemanticVersion.parse("1.0.0"),
                SemanticVersion.parse("2.0.0")));
        }
        return new UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UPDATE_AVAILABLE,
            new UpdatePlan(1, changes, Collections.emptyList()), Collections.<BlockedReason>emptyList(), null);
    }
}
