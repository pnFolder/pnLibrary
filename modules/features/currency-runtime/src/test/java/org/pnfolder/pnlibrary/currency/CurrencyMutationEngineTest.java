package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;

class CurrencyMutationEngineTest {
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);

    @Test
    void creditAddsAmountToTarget() {
        CurrencyMutation mutation = CurrencyMutationEngine.evaluate(
                request(CurrencyTransactionType.CREDIT, "2.50", null, account()), null, decimal("10.00"), descriptor);

        assertEquals(CurrencyTransactionStatus.COMMITTED, mutation.getStatus());
        assertEquals(decimal("12.50"), mutation.getTargetAfter());
    }

    @Test
    void insufficientDebitIsRejectedWithoutChangingSource() {
        CurrencyMutation mutation = CurrencyMutationEngine.evaluate(
                request(CurrencyTransactionType.DEBIT, "10.01", account(), null), decimal("10.00"), null, descriptor);

        assertEquals(CurrencyTransactionStatus.REJECTED, mutation.getStatus());
        assertEquals(decimal("10.00"), mutation.getSourceAfter());
        assertEquals("Insufficient funds", mutation.getFailure());
    }

    @Test
    void transferUpdatesBothBalancesAsOneTransition() {
        CurrencyMutation mutation = CurrencyMutationEngine.evaluate(
                request(CurrencyTransactionType.TRANSFER, "3.25", account(), account()), decimal("8.00"), decimal("1.50"),
                descriptor);

        assertEquals(CurrencyTransactionStatus.COMMITTED, mutation.getStatus());
        assertEquals(decimal("4.75"), mutation.getSourceAfter());
        assertEquals(decimal("4.75"), mutation.getTargetAfter());
    }

    private static CurrencyTransactionRequest request(CurrencyTransactionType type, String amount, CurrencyAccount source,
                                                      CurrencyAccount target) {
        return new CurrencyTransactionRequest(type, decimal(amount), source, target, CurrencyActor.system("test"), "test");
    }

    private static CurrencyAccount account() {
        return new CurrencyAccount(UUID.randomUUID());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
