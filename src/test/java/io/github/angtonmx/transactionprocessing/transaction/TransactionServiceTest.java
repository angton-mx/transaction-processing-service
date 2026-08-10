package io.github.angtonmx.transactionprocessing.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private TransactionProvider provider;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        provider = mock(TransactionProvider.class);
        service = new TransactionService(provider);
    }

    @Test
    void returnsApprovedProviderResult() {
        Transaction transaction = validTransaction();
        ProviderResult providerResult = new ProviderResult(
                "provider-transaction-123",
                ProviderStatus.APPROVED,
                new BigDecimal("1500.00"),
                Instant.parse("2026-08-09T18:00:00Z"),
                "00",
                "Approved");
        when(provider.execute(transaction)).thenReturn(providerResult);

        ProviderResult result = service.execute(transaction);

        verify(provider, times(1)).execute(transaction);
        assertThat(result).isSameAs(providerResult);
    }

    @Test
    void returnsRejectedProviderResult() {
        Transaction transaction = validTransaction();
        ProviderResult providerResult = new ProviderResult(
                "provider-transaction-456",
                ProviderStatus.REJECTED,
                new BigDecimal("500.00"),
                Instant.parse("2026-08-09T18:01:00Z"),
                "INSUFFICIENT_FUNDS",
                "Insufficient funds");
        when(provider.execute(transaction)).thenReturn(providerResult);

        ProviderResult result = service.execute(transaction);

        verify(provider, times(1)).execute(transaction);
        assertThat(result).isSameAs(providerResult);
    }

    @Test
    void rejectsNullTransactionWithoutCallingProvider() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.execute(null));

        verifyNoInteractions(provider);
    }

    @Test
    void propagatesProviderFailureWithoutRetry() {
        Transaction transaction = validTransaction();
        IllegalStateException providerFailure =
                new IllegalStateException("Provider unavailable");
        when(provider.execute(transaction)).thenThrow(providerFailure);

        assertThatThrownBy(() -> service.execute(transaction))
                .isSameAs(providerFailure);
        verify(provider, times(1)).execute(transaction);
    }

    private Transaction validTransaction() {
        return new Transaction(
                "account-123",
                TransactionType.DEBIT,
                new BigDecimal("100.00"),
                "MXN",
                "Test transaction");
    }
}
