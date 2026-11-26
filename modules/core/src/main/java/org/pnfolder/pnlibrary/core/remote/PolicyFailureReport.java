package org.pnfolder.pnlibrary.core.remote;

import java.net.URI;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyExplanation;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** What the administrator is told when a policy could not be checked and the plugin must not run. */
final class PolicyFailureReport {
    private PolicyFailureReport() {
    }

    /** Prints the "check failed" card and writes the failure to the log. */
    static void deny(RemotePolicyNoticeRenderer notices, Object owner, PluginMetadata metadata,
                     RemotePolicySettings policy, PnLogger logger, Throwable error) {
        RemotePolicyExplanation.Builder explanation = RemotePolicyExplanation.builder("Ошибка проверки")
            .child(RemotePolicyFailureInterpreter.message(error));
        if (RemotePolicyFailureInterpreter.isNetwork(error)) {
            explanation.child("Для запуска нужен интернет: откройте серверу доступ к " + hostOf(policy.getSource()));
        }
        notices.render(owner, metadata, policy, false, explanation.build());
        log(logger, metadata, error);
    }

    /** Prints the "could not check, keeps running" card. */
    static void unchecked(RemotePolicyNoticeRenderer notices, Object owner, PluginMetadata metadata,
                          RemotePolicySettings policy, Throwable error) {
        notices.unchecked(owner, metadata, policy, RemotePolicyFailureInterpreter.message(error),
            RemotePolicyFailureInterpreter.isNetwork(error));
    }

    // A server without internet or without a JDK is the administrator's setup, not an error to send to telemetry.
    private static void log(PnLogger logger, PluginMetadata metadata, Throwable error) {
        String message = "Remote policy failed for " + metadata.getName();
        if (RemotePolicyFailureInterpreter.isEnvironment(error)) {
            logger.warning(message);
        } else {
            logger.error(message, error);
        }
    }

    private static String hostOf(String source) {
        String host = Failures.runCatchingNonFatal(() -> new URI(source).getHost()).getOrNull();
        return host != null && !host.trim().isEmpty() ? host : "хосту политики";
    }
}
