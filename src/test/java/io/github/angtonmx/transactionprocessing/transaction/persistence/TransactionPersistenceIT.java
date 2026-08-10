package io.github.angtonmx.transactionprocessing.transaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.angtonmx.transactionprocessing.transaction.ProviderResult;
import io.github.angtonmx.transactionprocessing.transaction.ProviderStatus;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Testcontainers
@Transactional
class TransactionPersistenceIT {

    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_BALANCE = new BigDecimal("500.00");
    private static final Instant PROVIDER_EXECUTED_AT =
            Instant.parse("2026-08-10T12:34:56.123456Z");

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
    private TransactionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesExecutedTransaction() {
        Transaction transaction = transaction(TransactionType.CREDIT, DEFAULT_AMOUNT, "salary");
        ProviderResult providerResult = approvedResult(DEFAULT_BALANCE);
        TransactionEntity entity = TransactionEntity.executed(transaction, providerResult);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();

        TransactionEntity saved = repository.saveAndFlush(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(saved.getProviderStatus()).isEqualTo(ProviderStatus.APPROVED);
        assertThat(saved.getProviderTransactionId())
                .isEqualTo(providerResult.providerTransactionId());
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo(providerResult.balance());
        assertThat(saved.getProviderExecutedAt()).isEqualTo(providerResult.executedAt());
        assertThat(saved.getProviderCode()).isNull();
        assertThat(saved.getProviderMessage()).isNull();
        assertThat(saved.getErrorMessage()).isNull();

        DatabaseIdentity databaseIdentity = loadDatabaseIdentity(saved.getId());
        assertThat(databaseIdentity.id()).isEqualTo(saved.getId());
        assertThat(databaseIdentity.createdAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void savesRejectedTransaction() {
        Transaction transaction = transaction(TransactionType.DEBIT, DEFAULT_AMOUNT, "purchase");
        ProviderResult providerResult = rejectedResult();

        TransactionEntity saved = repository.saveAndFlush(
                TransactionEntity.rejected(transaction, providerResult));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(saved.getProviderStatus()).isEqualTo(ProviderStatus.REJECTED);
        assertThat(saved.getProviderCode()).isEqualTo(providerResult.code());
        assertThat(saved.getProviderMessage()).isEqualTo(providerResult.message());
        assertThat(saved.getProviderTransactionId()).isNull();
        assertThat(saved.getBalanceAfter()).isNull();
        assertThat(saved.getProviderExecutedAt()).isNull();
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void savesFailedTransaction() {
        Transaction transaction = transaction(TransactionType.CREDIT, DEFAULT_AMOUNT, "transfer");

        TransactionEntity saved = repository.saveAndFlush(
                TransactionEntity.failed(transaction, "Provider request timed out"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getProviderStatus()).isNull();
        assertThat(saved.getProviderTransactionId()).isNull();
        assertThat(saved.getBalanceAfter()).isNull();
        assertThat(saved.getProviderExecutedAt()).isNull();
        assertThat(saved.getProviderCode()).isNull();
        assertThat(saved.getProviderMessage()).isNull();
        assertThat(saved.getErrorMessage()).isEqualTo("Provider request timed out");
    }

    @Test
    void storesEnumsAsStrings() {
        TransactionEntity executed = repository.saveAndFlush(TransactionEntity.executed(
                transaction(TransactionType.DEBIT, DEFAULT_AMOUNT, "executed"),
                approvedResult(DEFAULT_BALANCE)));
        TransactionEntity rejected = repository.saveAndFlush(TransactionEntity.rejected(
                transaction(TransactionType.CREDIT, DEFAULT_AMOUNT, "rejected"),
                rejectedResult()));
        TransactionEntity failed = repository.saveAndFlush(TransactionEntity.failed(
                transaction(TransactionType.CREDIT, DEFAULT_AMOUNT, "failed"),
                "Provider unavailable"));

        List<DatabaseEnums> storedEnums = jdbcTemplate.query("""
                SELECT type, status, provider_status
                FROM transactions
                WHERE id IN (?, ?, ?)
                """, (resultSet, rowNumber) -> new DatabaseEnums(
                resultSet.getString("type"),
                resultSet.getString("status"),
                resultSet.getString("provider_status")),
                executed.getId(), rejected.getId(), failed.getId());

        assertThat(storedEnums)
                .extracting(DatabaseEnums::type, DatabaseEnums::status,
                        DatabaseEnums::providerStatus)
                .containsExactlyInAnyOrder(
                        tuple("DEBIT", "EXECUTED", "APPROVED"),
                        tuple("CREDIT", "REJECTED", "REJECTED"),
                        tuple("CREDIT", "FAILED", null));
    }

    @Test
    void findsPersistedTransactionById() {
        Transaction transaction = transaction(TransactionType.DEBIT, DEFAULT_AMOUNT, "purchase");
        ProviderResult providerResult = approvedResult(DEFAULT_BALANCE);
        UUID id = repository.saveAndFlush(
                TransactionEntity.executed(transaction, providerResult)).getId();
        entityManager.clear();

        TransactionEntity found = repository.findById(id).orElseThrow();

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getAccountId()).isEqualTo(transaction.accountId());
        assertThat(found.getType()).isEqualTo(transaction.type());
        assertThat(found.getAmount()).isEqualByComparingTo(transaction.amount());
        assertThat(found.getCurrency()).isEqualTo(transaction.currency());
        assertThat(found.getDescription()).isEqualTo(transaction.description());
        assertThat(found.getStatus()).isEqualTo(TransactionStatus.EXECUTED);
        assertThat(found.getProviderStatus()).isEqualTo(providerResult.status());
        assertThat(found.getProviderTransactionId())
                .isEqualTo(providerResult.providerTransactionId());
        assertThat(found.getBalanceAfter()).isEqualByComparingTo(providerResult.balance());
        assertThat(found.getProviderExecutedAt()).isEqualTo(providerResult.executedAt());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsNullDescription() {
        Transaction transaction = transaction(TransactionType.CREDIT, DEFAULT_AMOUNT, null);
        UUID id = repository.saveAndFlush(
                TransactionEntity.failed(transaction, "Provider unavailable")).getId();
        entityManager.clear();

        TransactionEntity found = repository.findById(id).orElseThrow();

        assertThat(found.getDescription()).isNull();
    }

    @Test
    void preservesBigDecimalValue() {
        BigDecimal preciseAmount = new BigDecimal("1.001");
        Transaction transaction = transaction(TransactionType.CREDIT, preciseAmount, "precise");
        UUID id = repository.saveAndFlush(
                TransactionEntity.failed(transaction, "Provider unavailable")).getId();
        entityManager.clear();

        TransactionEntity found = repository.findById(id).orElseThrow();

        assertThat(found.getAmount()).isEqualByComparingTo(preciseAmount);
    }

    @Test
    void supportsJpaWithoutPublicSetters() throws NoSuchMethodException {
        assertThat(Arrays.stream(TransactionEntity.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set")))
                .isEmpty();

        int constructorModifiers = TransactionEntity.class
                .getDeclaredConstructor()
                .getModifiers();
        assertThat(Modifier.isProtected(constructorModifiers)).isTrue();
    }

    private Transaction transaction(
            TransactionType type,
            BigDecimal amount,
            String description) {
        return new Transaction(
                "account-" + UUID.randomUUID(),
                type,
                amount,
                "MXN",
                description);
    }

    private ProviderResult approvedResult(BigDecimal balance) {
        return new ProviderResult(
                "provider-" + UUID.randomUUID(),
                ProviderStatus.APPROVED,
                balance,
                PROVIDER_EXECUTED_AT,
                null,
                null);
    }

    private ProviderResult rejectedResult() {
        return new ProviderResult(
                null,
                ProviderStatus.REJECTED,
                null,
                null,
                "DECLINED",
                "Transaction rejected");
    }

    private DatabaseIdentity loadDatabaseIdentity(UUID id) {
        return jdbcTemplate.queryForObject("""
                SELECT id, created_at
                FROM transactions
                WHERE id = ?
                """, (resultSet, rowNumber) -> new DatabaseIdentity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()), id);
    }

    private record DatabaseIdentity(UUID id, Instant createdAt) {
    }

    private record DatabaseEnums(String type, String status, String providerStatus) {
    }
}
