package ru.privatenull.pncases.policy;

import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult;

/** Example policy compiled as Java 8 and uploaded separately from pnCases. */
public final class RemotePolicy implements ru.privatenull.pnlibrary.api.remote.RemotePolicy {
    private static final String MINIMUM_PLUGIN_VERSION = "2.4.0";

    @Override
    public RemotePolicyResult check(RemotePolicyContext context) {
        String installed = context.getProduct().getVersion();

        if (!atLeast(installed, MINIMUM_PLUGIN_VERSION)) {
            return RemotePolicyResult.deny(
                "Установлена версия " + installed
                    + ", требуется " + MINIMUM_PLUGIN_VERSION
                    + ". Скачайте новую версию плагина."
            );
        }

        return RemotePolicyResult.allow();
    }

    private static boolean atLeast(String actual, String minimum) {
        int[] left = numbers(actual);
        int[] right = numbers(minimum);
        for (int index = 0; index < 3; index++) {
            if (left[index] != right[index]) return left[index] > right[index];
        }
        return true;
    }

    private static int[] numbers(String value) {
        String normalized = value == null ? "0.0.0" : value.trim().replaceFirst("^[vV]", "");
        String[] parts = normalized.split("[-+.]", 4);
        int[] result = new int[] { 0, 0, 0 };
        for (int index = 0; index < result.length && index < parts.length; index++) {
            try { result[index] = Integer.parseInt(parts[index]); }
            catch (NumberFormatException ignored) { result[index] = 0; }
        }
        return result;
    }
}
