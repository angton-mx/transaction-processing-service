package io.github.angtonmx.transactionprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransactionConstraintsIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void insertsValidExecutedTransaction() {
        assertThat(insert(executedTransaction())).isEqualTo(1);
    }

    @Test
    void insertsValidRejectedTransaction() {
        assertThat(insert(rejectedTransaction())).isEqualTo(1);
    }

    @Test
    void insertsValidFailedTransaction() {
        assertThat(insert(failedTransaction())).isEqualTo(1);
    }

    @Test
    void rejectsBlankAccountId() {
        TransactionInsert transaction = executedTransaction();
        transaction.accountId = "   ";

        assertConstraintViolation(transaction, "chk_transactions_account_id");
    }

    @Test
    void rejectsInvalidType() {
        TransactionInsert transaction = executedTransaction();
        transaction.type = "TRANSFER";

        assertConstraintViolation(transaction, "chk_transactions_type");
    }

    @Test
    void rejectsAmountEqualToMinimumBoundary() {
        TransactionInsert transaction = executedTransaction();
        transaction.amount = new BigDecimal("1.00");

        assertConstraintViolation(transaction, "chk_transactions_amount");
    }

    @Test
    void rejectsAmountBelowMinimumBoundary() {
        TransactionInsert transaction = executedTransaction();
        transaction.amount = new BigDecimal("0.99");

        assertConstraintViolation(transaction, "chk_transactions_amount");
    }

    @Test
    void rejectsDebitAboveMaximum() {
        TransactionInsert transaction = executedTransaction();
        transaction.type = "DEBIT";
        transaction.amount = new BigDecimal("10000.01");

        assertConstraintViolation(transaction, "chk_transactions_debit_limit");
    }

    @Test
    void rejectsCurrencyOtherThanExactUppercaseMxn() {
        TransactionInsert transaction = executedTransaction();
        transaction.currency = "mxn";

        assertConstraintViolation(transaction, "chk_transactions_currency");
    }

    @Test
    void rejectsInvalidTransactionStatus() {
        TransactionInsert transaction = failedTransaction();
        transaction.status = "PENDING";

        assertRejected(transaction);
    }

    @Test
    void rejectsInvalidProviderStatus() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerStatus = "UNKNOWN";

        assertRejected(transaction);
    }

    @Test
    void rejectsExecutedWithRejectedProviderStatus() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerStatus = "REJECTED";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithNullProviderStatus() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerStatus = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithoutProviderTransactionId() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerTransactionId = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithoutBalanceAfter() {
        TransactionInsert transaction = executedTransaction();
        transaction.balanceAfter = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithoutProviderExecutedAt() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerExecutedAt = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithProviderCode() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerCode = "UNEXPECTED";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithProviderMessage() {
        TransactionInsert transaction = executedTransaction();
        transaction.providerMessage = "unexpected";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsExecutedWithErrorMessage() {
        TransactionInsert transaction = executedTransaction();
        transaction.errorMessage = "unexpected";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithApprovedProviderStatus() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerStatus = "APPROVED";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithNullProviderStatus() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerStatus = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithoutProviderCode() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerCode = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithoutProviderMessage() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerMessage = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithProviderTransactionId() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerTransactionId = "provider-" + UUID.randomUUID();

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithBalanceAfter() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.balanceAfter = new BigDecimal("500.00");

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsRejectedWithProviderExecutedAt() {
        TransactionInsert transaction = rejectedTransaction();
        transaction.providerExecutedAt = OffsetDateTime.now(ZoneOffset.UTC);

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsFailedWithProviderStatus() {
        TransactionInsert transaction = failedTransaction();
        transaction.providerStatus = "APPROVED";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsFailedWithoutErrorMessage() {
        TransactionInsert transaction = failedTransaction();
        transaction.errorMessage = null;

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsFailedWithProviderResultFields() {
        TransactionInsert transaction = failedTransaction();
        transaction.providerTransactionId = "provider-" + UUID.randomUUID();
        transaction.balanceAfter = new BigDecimal("500.00");
        transaction.providerExecutedAt = OffsetDateTime.now(ZoneOffset.UTC);
        transaction.providerCode = "UNEXPECTED";
        transaction.providerMessage = "unexpected";

        assertConstraintViolation(transaction, "chk_transactions_result");
    }

    @Test
    void rejectsDuplicateNonNullProviderTransactionId() {
        TransactionInsert first = executedTransaction();
        TransactionInsert second = executedTransaction();
        second.providerTransactionId = first.providerTransactionId;

        assertThat(insert(first)).isEqualTo(1);
        assertConstraintViolation(second, "uq_transactions_provider_id");
    }

    @Test
    void allowsMultipleNullProviderTransactionIds() {
        TransactionInsert first = failedTransaction();
        TransactionInsert second = failedTransaction();

        assertThat(insert(first)).isEqualTo(1);
        assertThat(insert(second)).isEqualTo(1);
    }

    private int insert(TransactionInsert transaction) {
        return jdbcTemplate().update("""
                INSERT INTO transactions (
                    id,
                    account_id,
                    type,
                    amount,
                    currency,
                    description,
                    status,
                    provider_status,
                    provider_transaction_id,
                    balance_after,
                    provider_executed_at,
                    provider_code,
                    provider_message,
                    error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                transaction.id,
                transaction.accountId,
                transaction.type,
                transaction.amount,
                transaction.currency,
                transaction.description,
                transaction.status,
                transaction.providerStatus,
                transaction.providerTransactionId,
                transaction.balanceAfter,
                transaction.providerExecutedAt,
                transaction.providerCode,
                transaction.providerMessage,
                transaction.errorMessage);
    }

    private void assertConstraintViolation(
            TransactionInsert transaction,
            String expectedConstraint) {
        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> insert(transaction));

        assertThat(exception)
                .as("PostgreSQL must reject the invalid transaction")
                .isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains(expectedConstraint);
    }

    private void assertRejected(TransactionInsert transaction) {
        assertThat(catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> insert(transaction)))
                .as("PostgreSQL must reject the invalid transaction")
                .isNotNull();
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    private TransactionInsert executedTransaction() {
        TransactionInsert transaction = new TransactionInsert();
        transaction.status = "EXECUTED";
        transaction.providerStatus = "APPROVED";
        transaction.providerTransactionId = "provider-" + UUID.randomUUID();
        transaction.balanceAfter = new BigDecimal("500.00");
        transaction.providerExecutedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return transaction;
    }

    private TransactionInsert rejectedTransaction() {
        TransactionInsert transaction = new TransactionInsert();
        transaction.status = "REJECTED";
        transaction.providerStatus = "REJECTED";
        transaction.providerCode = "DECLINED";
        transaction.providerMessage = "Transaction rejected";
        return transaction;
    }

    private TransactionInsert failedTransaction() {
        TransactionInsert transaction = new TransactionInsert();
        transaction.status = "FAILED";
        transaction.errorMessage = "Provider unavailable";
        return transaction;
    }

    private static final class TransactionInsert {

        private UUID id = UUID.randomUUID();
        private String accountId = "account-" + UUID.randomUUID();
        private String type = "CREDIT";
        private BigDecimal amount = new BigDecimal("100.00");
        private String currency = "MXN";
        private String description = "integration test transaction";
        private String status;
        private String providerStatus;
        private String providerTransactionId;
        private BigDecimal balanceAfter;
        private OffsetDateTime providerExecutedAt;
        private String providerCode;
        private String providerMessage;
        private String errorMessage;
    }
}
