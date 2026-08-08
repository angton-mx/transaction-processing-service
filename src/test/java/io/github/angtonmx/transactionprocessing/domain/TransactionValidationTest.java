package io.github.angtonmx.transactionprocessing.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionValidationTest {

    @Test
    void rejectsAmountEqualToOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, "1.00", "MXN"));
    }

    @Test
    void rejectsAmountBelowOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, "0.99", "MXN"));
    }

    @Test
    void acceptsAmountJustAboveOne() {
        assertThatCode(() -> transaction(TransactionType.CREDIT, "1.01", "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsDebitAtMaximumAmount() {
        assertThatCode(() -> transaction(TransactionType.DEBIT, "10000.00", "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDebitAboveMaximumAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.DEBIT, "10000.01", "MXN"));
    }

    @Test
    void acceptsCreditAboveDebitMaximumAmount() {
        assertThatCode(() -> transaction(TransactionType.CREDIT, "10000.01", "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsSignificantlyLargerCredit() {
        assertThatCode(() -> transaction(TransactionType.CREDIT, "1000000000.00", "MXN"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMxnCurrency() {
        assertThatCode(() -> transaction(TransactionType.CREDIT, "100.00", "MXN"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {"USD", "EUR"})
    void rejectsUnsupportedCurrency(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, "100.00", currency));
    }

    @Test
    void rejectsTransactionThatViolatesMultipleApplicableRules() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.DEBIT, "1.00", "USD"));
    }

    private Transaction transaction(TransactionType type, String amount, String currency) {
        return new Transaction(
                "account-123",
                type,
                new BigDecimal(amount),
                currency,
                "Test transaction");
    }
}
