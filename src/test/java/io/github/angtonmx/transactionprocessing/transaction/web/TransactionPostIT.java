package io.github.angtonmx.transactionprocessing.transaction.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionPostIT {

    private static final String EXECUTE_PATH = "/provider/v1/execute";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23");

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(options().dynamicPort());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startWireMock() {
        WIRE_MOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("provider.base-url", WIRE_MOCK::baseUrl);
    }

    @Test
    void createsAndPersistsExecutedTransaction() throws Exception {
        WIRE_MOCK.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "transactionId": "provider-transaction-123",
                                  "status": "APPROVED",
                                  "balance": 8500.00,
                                  "executedAt": "2026-08-10T15:00:00Z"
                                }
                                """)));

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/transactions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-123456",
                                  "type": "CREDIT",
                                  "amount": 1500.00,
                                  "currency": "MXN",
                                  "description": "Transferencia recibida"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.id", isA(String.class)))
                .andExpect(jsonPath("$.accountId").value("acc-123456"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.description")
                        .value("Transferencia recibida"))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.providerTransactionId")
                        .value("provider-transaction-123"))
                .andExpect(jsonPath("$.balanceAfter").value(8500.0))
                .andExpect(jsonPath("$.createdAt", isA(String.class)))
                .andExpect(jsonPath("$.providerStatus").doesNotExist())
                .andExpect(jsonPath("$.providerExecutedAt").doesNotExist())
                .andExpect(jsonPath("$.providerCode").doesNotExist())
                .andExpect(jsonPath("$.providerMessage").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andReturn();

        JsonNode response = responseBody(result);
        UUID responseId = UUID.fromString(response.get("id").asText());
        assertThat(Instant.parse(response.get("createdAt").asText()))
                .isNotNull();

        verifyProviderRequest(
                "acc-123456",
                "CREDIT",
                "1500.00");

        PersistedTransaction persisted = loadTransaction(responseId);
        assertThat(persisted.id()).isEqualTo(responseId);
        assertThat(persisted.accountId()).isEqualTo("acc-123456");
        assertThat(persisted.type()).isEqualTo("CREDIT");
        assertThat(persisted.amount()).isEqualByComparingTo("1500.00");
        assertThat(persisted.currency()).isEqualTo("MXN");
        assertThat(persisted.description())
                .isEqualTo("Transferencia recibida");
        assertThat(persisted.status()).isEqualTo("EXECUTED");
        assertThat(persisted.providerStatus()).isEqualTo("APPROVED");
        assertThat(persisted.providerTransactionId())
                .isEqualTo("provider-transaction-123");
        assertThat(persisted.balanceAfter())
                .isEqualByComparingTo("8500.00");
        assertThat(persisted.providerExecutedAt())
                .isEqualTo(Instant.parse("2026-08-10T15:00:00Z"));
        assertThat(persisted.providerCode()).isNull();
        assertThat(persisted.providerMessage()).isNull();
        assertThat(persisted.errorMessage()).isNull();
        assertThat(persisted.createdAt()).isNotNull();
    }

    @Test
    void createsAndPersistsRejectedTransaction() throws Exception {
        WIRE_MOCK.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "code": "INSUFFICIENT_FUNDS",
                                  "message": "Insufficient funds"
                                }
                                """)));

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/transactions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-rejected-654321",
                                  "type": "DEBIT",
                                  "amount": 200.00,
                                  "currency": "MXN",
                                  "description": "Compra rechazada"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.id", isA(String.class)))
                .andExpect(jsonPath("$.accountId")
                        .value("acc-rejected-654321"))
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.amount").value(200.0))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.description")
                        .value("Compra rechazada"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.providerTransactionId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.balanceAfter").value(nullValue()))
                .andExpect(jsonPath("$.createdAt", isA(String.class)))
                .andExpect(jsonPath("$.providerStatus").doesNotExist())
                .andExpect(jsonPath("$.providerExecutedAt").doesNotExist())
                .andExpect(jsonPath("$.providerCode").doesNotExist())
                .andExpect(jsonPath("$.providerMessage").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andReturn();

        JsonNode response = responseBody(result);
        UUID responseId = UUID.fromString(response.get("id").asText());
        assertThat(Instant.parse(response.get("createdAt").asText()))
                .isNotNull();

        verifyProviderRequest(
                "acc-rejected-654321",
                "DEBIT",
                "200.00");

        PersistedTransaction persisted = loadTransaction(responseId);
        assertThat(persisted.id()).isEqualTo(responseId);
        assertThat(persisted.accountId())
                .isEqualTo("acc-rejected-654321");
        assertThat(persisted.type()).isEqualTo("DEBIT");
        assertThat(persisted.amount()).isEqualByComparingTo("200.00");
        assertThat(persisted.currency()).isEqualTo("MXN");
        assertThat(persisted.description()).isEqualTo("Compra rechazada");
        assertThat(persisted.status()).isEqualTo("REJECTED");
        assertThat(persisted.providerStatus()).isEqualTo("REJECTED");
        assertThat(persisted.providerTransactionId()).isNull();
        assertThat(persisted.balanceAfter()).isNull();
        assertThat(persisted.providerExecutedAt()).isNull();
        assertThat(persisted.providerCode())
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(persisted.providerMessage())
                .isEqualTo("Insufficient funds");
        assertThat(persisted.errorMessage()).isNull();
        assertThat(persisted.createdAt()).isNotNull();
    }

    private void verifyProviderRequest(
            String accountId,
            String type,
            String amount) {
        WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));

        WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH))
                .withHeader(
                        HttpHeaders.CONTENT_TYPE,
                        equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("""
                        {
                          "accountId": "%s",
                          "type": "%s",
                          "amount": %s,
                          "currency": "MXN"
                        }
                        """.formatted(accountId, type, amount), true, false)));
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private PersistedTransaction loadTransaction(UUID id) {
        return jdbcTemplate.queryForObject("""
                SELECT id,
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
                       error_message,
                       created_at
                FROM transactions
                WHERE id = ?
                """, (resultSet, rowNumber) -> {
            OffsetDateTime providerExecutedAt = resultSet.getObject(
                    "provider_executed_at",
                    OffsetDateTime.class);
            OffsetDateTime createdAt = resultSet.getObject(
                    "created_at",
                    OffsetDateTime.class);
            return new PersistedTransaction(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("account_id"),
                    resultSet.getString("type"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getString("currency"),
                    resultSet.getString("description"),
                    resultSet.getString("status"),
                    resultSet.getString("provider_status"),
                    resultSet.getString("provider_transaction_id"),
                    resultSet.getBigDecimal("balance_after"),
                    providerExecutedAt == null
                            ? null
                            : providerExecutedAt.toInstant(),
                    resultSet.getString("provider_code"),
                    resultSet.getString("provider_message"),
                    resultSet.getString("error_message"),
                    createdAt.toInstant());
        }, id);
    }

    private record PersistedTransaction(
            UUID id,
            String accountId,
            String type,
            BigDecimal amount,
            String currency,
            String description,
            String status,
            String providerStatus,
            String providerTransactionId,
            BigDecimal balanceAfter,
            Instant providerExecutedAt,
            String providerCode,
            String providerMessage,
            String errorMessage,
            Instant createdAt) {
    }
}
