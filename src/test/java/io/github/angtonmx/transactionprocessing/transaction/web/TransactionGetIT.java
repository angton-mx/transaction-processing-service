package io.github.angtonmx.transactionprocessing.transaction.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionGetIT {

    private static final Instant BASE_CREATED_AT =
            Instant.parse("2026-08-10T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @BeforeEach
    void seedTransactions() {
        jdbcTemplate.update("DELETE FROM transactions");
        for (int position = 1; position <= 22; position++) {
            insertTransaction(position);
        }
    }

    @Test
    void usesDefaultPaginationAndReturnsPublicFields() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0]", aMapWithSize(10)))
                .andExpect(jsonPath("$[0].id").value(id(22).toString()))
                .andExpect(jsonPath("$[19].id").value(id(3).toString()))
                .andExpect(jsonPath("$[0].providerStatus").doesNotExist())
                .andExpect(jsonPath("$[0].providerExecutedAt").doesNotExist())
                .andExpect(jsonPath("$[0].providerCode").doesNotExist())
                .andExpect(jsonPath("$[0].providerMessage").doesNotExist())
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist());
    }

    @Test
    void appliesExplicitPageAndLimit() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("page", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(
                        id(20).toString(),
                        id(19).toString())));
    }

    @Test
    void filtersByAccountId() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("accountId", "account-filter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].accountId", everyItem(
                        is("account-filter"))));
    }

    @Test
    void filtersByStatus() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].status", everyItem(
                        is("REJECTED"))));
    }

    @Test
    void filtersByType() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].type", everyItem(is("DEBIT"))));
    }

    @Test
    void appliesCombinedFilters() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("accountId", "combined-account")
                        .param("status", "REJECTED")
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id(11).toString()));
    }

    @Test
    void ordersEqualTimestampsByIdDescending() throws Exception {
        mockMvc.perform(get("/transactions")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        id(22).toString(),
                        id(21).toString())));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/transactions").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidLimit() throws Exception {
        mockMvc.perform(get("/transactions").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsLimitAboveMaximum() throws Exception {
        mockMvc.perform(get("/transactions").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    private void insertTransaction(int position) {
        String accountId = accountId(position);
        TransactionStatus status = transactionStatus(position);
        TransactionType type = type(position);
        Instant createdAt = position >= 21
                ? BASE_CREATED_AT.plusSeconds(22 * 60L)
                : BASE_CREATED_AT.plusSeconds(position * 60L);

        String providerStatus = switch (status) {
            case EXECUTED -> "APPROVED";
            case REJECTED -> "REJECTED";
            case FAILED -> null;
        };
        String providerTransactionId = status == TransactionStatus.EXECUTED
                ? "provider-" + position
                : null;
        BigDecimal balanceAfter = status == TransactionStatus.EXECUTED
                ? new BigDecimal("500.00")
                : null;
        OffsetDateTime providerExecutedAt = status == TransactionStatus.EXECUTED
                ? OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
                : null;
        String providerCode = status == TransactionStatus.REJECTED
                ? "REJECTED_" + position
                : null;
        String providerMessage = status == TransactionStatus.REJECTED
                ? "Rejected transaction " + position
                : null;
        String errorMessage = status == TransactionStatus.FAILED
                ? "Provider transport failure"
                : null;

        jdbcTemplate.update("""
                INSERT INTO transactions (
                    id, account_id, type, amount, currency, description,
                    status, provider_status, provider_transaction_id,
                    balance_after, provider_executed_at, provider_code,
                    provider_message, error_message, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id(position),
                accountId,
                type.name(),
                new BigDecimal("100.00"),
                "MXN",
                "transaction-" + position,
                status.name(),
                providerStatus,
                providerTransactionId,
                balanceAfter,
                providerExecutedAt,
                providerCode,
                providerMessage,
                errorMessage,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    private String accountId(int position) {
        return switch (position) {
            case 5, 6 -> "account-filter";
            case 11, 12 -> "combined-account";
            default -> "account-" + position;
        };
    }

    private TransactionStatus transactionStatus(int position) {
        return switch (position) {
            case 7 -> TransactionStatus.FAILED;
            case 8, 11, 13 -> TransactionStatus.REJECTED;
            default -> TransactionStatus.EXECUTED;
        };
    }

    private TransactionType type(int position) {
        return switch (position) {
            case 9, 10, 11, 13 -> TransactionType.DEBIT;
            default -> TransactionType.CREDIT;
        };
    }

    private UUID id(int position) {
        return new UUID(0L, position);
    }
}
