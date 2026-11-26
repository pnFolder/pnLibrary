package org.pnfolder.pnlibrary.bukkit.currency;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.pnfolder.pnlibrary.api.currency.CurrencyResult;

/** Operations that wait for the console to confirm them, by a short code. */
final class CurrencyConfirmationStore {
    private static final int MAX_PENDING = 1_000;
    private static final int TOKEN_LENGTH = 10;
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ConcurrentHashMap<String, PendingOperation> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    PendingOperation add(int timeoutSeconds, String currency, String description, String requestedBy,
                         Supplier<CompletionStage<CurrencyResult>> execute, Consumer<CurrencyResult> completed) {
        if (pending.size() >= MAX_PENDING) {
            return null;
        }
        String token = newToken();
        long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1_000L;
        PendingOperation operation = new PendingOperation(token, expiresAt, currency, description, requestedBy, execute,
            completed);
        pending.put(token, operation);
        return operation;
    }

    PendingOperation take(String token) {
        return pending.remove(token.toUpperCase(Locale.ROOT));
    }

    void discardExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private String newToken() {
        for (int attempt = 0; attempt < 32; attempt++) {
            StringBuilder value = new StringBuilder(TOKEN_LENGTH);
            for (int index = 0; index < TOKEN_LENGTH; index++) {
                value.append(TOKEN_ALPHABET.charAt(random.nextInt(TOKEN_ALPHABET.length())));
            }
            if (!pending.containsKey(value.toString())) {
                return value.toString();
            }
        }
        throw new IllegalStateException("Unable to allocate a currency confirmation code");
    }

    /** One operation waiting for its confirmation. */
    static final class PendingOperation {
        final String token;
        final long expiresAt;
        final String currency;
        final String description;
        final String requestedBy;
        final Supplier<CompletionStage<CurrencyResult>> execute;
        final Consumer<CurrencyResult> completed;

        PendingOperation(String token, long expiresAt, String currency, String description, String requestedBy,
                         Supplier<CompletionStage<CurrencyResult>> execute, Consumer<CurrencyResult> completed) {
            this.token = token;
            this.expiresAt = expiresAt;
            this.currency = currency;
            this.description = description;
            this.requestedBy = requestedBy;
            this.execute = execute;
            this.completed = completed;
        }
    }
}
