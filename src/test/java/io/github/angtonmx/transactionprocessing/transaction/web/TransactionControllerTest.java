package io.github.angtonmx.transactionprocessing.transaction.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.angtonmx.transactionprocessing.transaction.ProviderStatus;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionService;
import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;
import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionEntity;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final UUID EXECUTED_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant EXECUTED_CREATED_AT =
            Instant.parse("2026-08-10T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createsExecutedTransaction() throws Exception {
        TransactionEntity entity = mockEntity(
                EXECUTED_ID,
                "acc-123456",
                TransactionType.CREDIT,
                "1500.00",
                "MXN",
                "Transferencia recibida",
                TransactionStatus.EXECUTED,
                "provider-transaction-123",
                "8500.00",
                EXECUTED_CREATED_AT);
        when(entity.getProviderStatus()).thenReturn(ProviderStatus.APPROVED);
        when(entity.getProviderExecutedAt())
                .thenReturn(Instant.parse("2026-08-10T14:59:59Z"));
        when(entity.getProviderCode()).thenReturn("INTERNAL_CODE");
        when(entity.getProviderMessage()).thenReturn("Internal message");
        when(entity.getErrorMessage()).thenReturn("Internal error");
        when(transactionService.execute(any(Transaction.class)))
                .thenReturn(entity);

        mockMvc.perform(post("/transactions")
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
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.id").value(EXECUTED_ID.toString()))
                .andExpect(jsonPath("$.accountId").value("acc-123456"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount", isA(Number.class)))
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.description")
                        .value("Transferencia recibida"))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.providerTransactionId")
                        .value("provider-transaction-123"))
                .andExpect(jsonPath("$.balanceAfter", isA(Number.class)))
                .andExpect(jsonPath("$.balanceAfter").value(8500.0))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-08-10T15:00:00Z"))
                .andExpect(jsonPath("$.providerStatus").doesNotExist())
                .andExpect(jsonPath("$.providerExecutedAt").doesNotExist())
                .andExpect(jsonPath("$.providerCode").doesNotExist())
                .andExpect(jsonPath("$.providerMessage").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).execute(transactionCaptor.capture());
        Transaction transaction = transactionCaptor.getValue();
        assertThat(transaction.accountId()).isEqualTo("acc-123456");
        assertThat(transaction.type()).isEqualTo(TransactionType.CREDIT);
        assertThat(transaction.amount()).isEqualByComparingTo("1500.00");
        assertThat(transaction.currency()).isEqualTo("MXN");
        assertThat(transaction.description())
                .isEqualTo("Transferencia recibida");
    }

    @Test
    void createsRejectedTransaction() throws Exception {
        UUID rejectedId =
                UUID.fromString("22222222-2222-2222-2222-222222222222");
        TransactionEntity entity = mockEntity(
                rejectedId,
                "acc-123456",
                TransactionType.CREDIT,
                "1500.00",
                "MXN",
                "Transferencia recibida",
                TransactionStatus.REJECTED,
                null,
                null,
                Instant.parse("2026-08-10T15:05:00Z"));
        when(entity.getProviderStatus()).thenReturn(ProviderStatus.REJECTED);
        when(entity.getProviderCode()).thenReturn("INSUFFICIENT_FUNDS");
        when(entity.getProviderMessage()).thenReturn("Insufficient funds");
        when(transactionService.execute(any(Transaction.class)))
                .thenReturn(entity);

        mockMvc.perform(post("/transactions")
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
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$", aMapWithSize(10)))
                .andExpect(jsonPath("$.id").value(rejectedId.toString()))
                .andExpect(jsonPath("$.accountId").value("acc-123456"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount", isA(Number.class)))
                .andExpect(jsonPath("$.amount").value(1500.0))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.description")
                        .value("Transferencia recibida"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.providerTransactionId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.balanceAfter").value(nullValue()))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-08-10T15:05:00Z"))
                .andExpect(jsonPath("$.providerStatus").doesNotExist())
                .andExpect(jsonPath("$.providerExecutedAt").doesNotExist())
                .andExpect(jsonPath("$.providerCode").doesNotExist())
                .andExpect(jsonPath("$.providerMessage").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void mapsNullDescription() throws Exception {
        TransactionEntity entity = mockEntity(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "acc-123456",
                TransactionType.CREDIT,
                "1500.00",
                "MXN",
                null,
                TransactionStatus.EXECUTED,
                "provider-transaction-456",
                "8500.00",
                Instant.parse("2026-08-10T15:10:00Z"));
        when(transactionService.execute(any(Transaction.class)))
                .thenReturn(entity);

        mockMvc.perform(post("/transactions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-123456",
                                  "type": "CREDIT",
                                  "amount": 1500.00,
                                  "currency": "MXN",
                                  "description": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(nullValue()));

        ArgumentCaptor<Transaction> transactionCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).execute(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().description()).isNull();
    }

    private TransactionEntity mockEntity(
            UUID id,
            String accountId,
            TransactionType type,
            String amount,
            String currency,
            String description,
            TransactionStatus status,
            String providerTransactionId,
            String balanceAfter,
            Instant createdAt) {
        TransactionEntity entity = mock(TransactionEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getAccountId()).thenReturn(accountId);
        when(entity.getType()).thenReturn(type);
        when(entity.getAmount()).thenReturn(new BigDecimal(amount));
        when(entity.getCurrency()).thenReturn(currency);
        when(entity.getDescription()).thenReturn(description);
        when(entity.getStatus()).thenReturn(status);
        when(entity.getProviderTransactionId())
                .thenReturn(providerTransactionId);
        when(entity.getBalanceAfter()).thenReturn(
                balanceAfter == null ? null : new BigDecimal(balanceAfter));
        when(entity.getCreatedAt()).thenReturn(createdAt);
        return entity;
    }
}
