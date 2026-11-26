package org.pnfolder.pnlibrary.config;

import org.pnfolder.pnlibrary.api.config.ConfigSteps;
import org.pnfolder.pnlibrary.api.config.Value;

public final class StepsForTest extends ConfigSteps<StepSettings> {
    public StepsForTest() {
        version(2, changes -> changes.rename("pool-size", "pool"));
        version(3, changes -> {
            Value address = changes.get("address");
            changes.put("host", address.before(":"));
            changes.put("port", address.after(":").toInt());
            changes.remove("address");
        });
        version(4, changes -> {
            Value first = changes.get("first");
            Value second = changes.get("second");
            changes.put("first", second);
            changes.put("second", first);
        });
    }
}
