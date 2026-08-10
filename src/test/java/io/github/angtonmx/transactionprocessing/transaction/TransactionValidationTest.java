package io.github.angtonmx.transactionprocessing.transaction;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionValidationTest {

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {"1.00", "0.99", "0.00", "-1.00"})
    void rejectsAmountNotGreaterThanOne(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, amount, "MXN"));
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
    @ValueSource(strings = {"USD", "EUR", "mxn", "Mxn", ""})
    void rejectsUnsupportedCurrency(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, "100.00", currency));
    }

    @Test
    void rejectsNullCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transaction(TransactionType.CREDIT, "100.00", null));
    }

    @ParameterizedTest(name = "[{index}] accountId={0}")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingAccountId(String accountId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(
                        accountId,
                        TransactionType.CREDIT,
                        new BigDecimal("100.00"),
                        "MXN",
                        "Test transaction"));
    }

    @Test
    void rejectsNullType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(
                        "account-123",
                        null,
                        new BigDecimal("100.00"),
                        "MXN",
                        "Test transaction"));
    }

    @Test
    void rejectsNullAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(
                        "account-123",
                        TransactionType.CREDIT,
                        null,
                        "MXN",
                        "Test transaction"));
    }

    @Test
    void acceptsNullDescription() {
        assertThatCode(() -> new Transaction(
                "account-123",
                TransactionType.CREDIT,
                new BigDecimal("100.00"),
                "MXN",
                null))
                .doesNotThrowAnyException();
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
