package acceptance.policy;

import ru.privatenull.pnlibrary.api.remote.RemotePolicy;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyExplanation;
import ru.privatenull.pnlibrary.api.remote.RemotePolicyResult;

/** Local acceptance policy. Change MINIMUM_VERSION to test allow and deny decisions. */
public final class AcceptancePolicy implements RemotePolicy {
    private static final String MINIMUM_VERSION = "2.3.0";

    @Override
    public RemotePolicyResult check(RemotePolicyContext context) {
        String installed = context.getProduct().getVersion();
        if (compare(installed, MINIMUM_VERSION) < 0) {
            return RemotePolicyResult.deny(RemotePolicyExplanation.builder("Версия больше не поддерживается")
                .branch("Сравнение версий", versions -> versions
                    .child("Установлена: " + installed)
                    .child("Минимальная: " + MINIMUM_VERSION))
                .branch("Что делать", action -> action
                    .child("Скачайте актуальную версию плагина")
                    .branch("После замены файла", restart -> restart
                        .child("Полностью остановите сервер")
                        .child("Запустите сервер заново")))
                .build());
        }
        return RemotePolicyResult.allow();
    }

    private static int compare(String actual, String required) {
        int[] left = numbers(actual);
        int[] right = numbers(required);
        for (int index = 0; index < 3; index++) {
            if (left[index] != right[index]) return Integer.compare(left[index], right[index]);
        }
        return 0;
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
