package pnfolder.policy;

import ru.privatenull.pnlibrary.api.remote.RemotePolicy;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult;
import ru.privatenull.pnlibrary.api.version.SemanticVersion;

/**
 * Test remote policy for pnLibrary itself. Not published and not connected to any server.
 * Denies startup when the installed version is below {@link #MINIMUM_VERSION}.
 * Change the constant before this file is published.
 */
public final class PnLibraryPolicy implements RemotePolicy {
    /** Test threshold. Versions below it are denied. */
    private static final String MINIMUM_VERSION = "0.0.1";

    @Override
    public RemotePolicyResult check(RemotePolicyContext context) {
        String installed = context.getProduct().getVersion();
        SemanticVersion current = SemanticVersion.tryParse(installed);
        if (current == null) {
            return RemotePolicyResult.deny("Не удалось определить версию: " + installed);
        }
        if (current.compareTo(SemanticVersion.parse(MINIMUM_VERSION)) < 0) {
            return RemotePolicyResult.deny("Установлена версия " + installed
                + ", требуется " + MINIMUM_VERSION + " или новее. Скачайте новую версию плагина.");
        }
        return RemotePolicyResult.allow();
    }
}
