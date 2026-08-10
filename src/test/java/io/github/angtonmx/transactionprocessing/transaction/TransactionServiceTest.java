package io.github.angtonmx.transactionprocessing.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionEntity;
import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionRepository;

class TransactionServiceTest {

    private TransactionProvider provider;
    private TransactionRepository repository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        provider = mock(TransactionProvider.class);
        repository = mock(TransactionRepository.class);
        service = new TransactionService(provider, repository);
    }

    @Test
    void persistsApprovedProviderResult() {
        Transaction transaction = validTransaction();
        ProviderResult providerResult = new ProviderResult(
                "provider-transaction-123",
                ProviderStatus.APPROVED,
                new BigDecimal("1500.00"),
                Instant.parse("2026-08-09T18:00:00Z"),
                null,
                null);
        TransactionEntity repositoryResult =
                TransactionEntity.executed(transaction, providerResult);
        when(provider.execute(transaction)).thenReturn(providerResult);
        when(repository.save(any(TransactionEntity.class)))
                .thenReturn(repositoryResult);

        TransactionEntity result = service.execute(transaction);

        verify(provider, times(1)).execute(transaction);
        ArgumentCaptor<TransactionEntity> entityCaptor =
                ArgumentCaptor.forClass(TransactionEntity.class);
        verify(repository, times(1)).save(entityCaptor.capture());
        TransactionEntity persistedEntity = entityCaptor.getValue();
        assertThat(persistedEntity.getStatus())
                .isEqualTo(TransactionStatus.EXECUTED);
        assertThat(persistedEntity.getProviderStatus())
                .isEqualTo(ProviderStatus.APPROVED);
        assertThat(persistedEntity.getProviderTransactionId())
                .isEqualTo(providerResult.providerTransactionId());
        assertThat(persistedEntity.getBalanceAfter())
                .isEqualByComparingTo(providerResult.balance());
        assertThat(persistedEntity.getProviderExecutedAt())
                .isEqualTo(providerResult.executedAt());
        assertThat(persistedEntity.getProviderCode()).isNull();
        assertThat(persistedEntity.getProviderMessage()).isNull();
        assertThat(persistedEntity.getErrorMessage()).isNull();
        assertThat(result).isSameAs(repositoryResult);
    }

    @Test
    void persistsRejectedProviderResult() {
        Transaction transaction = validTransaction();
        ProviderResult providerResult = new ProviderResult(
                null,
                ProviderStatus.REJECTED,
                null,
                null,
                "INSUFFICIENT_FUNDS",
                "Insufficient funds");
        TransactionEntity repositoryResult =
                TransactionEntity.rejected(transaction, providerResult);
        when(provider.execute(transaction)).thenReturn(providerResult);
        when(repository.save(any(TransactionEntity.class)))
                .thenReturn(repositoryResult);

        TransactionEntity result = service.execute(transaction);

        verify(provider, times(1)).execute(transaction);
        ArgumentCaptor<TransactionEntity> entityCaptor =
                ArgumentCaptor.forClass(TransactionEntity.class);
        verify(repository, times(1)).save(entityCaptor.capture());
        TransactionEntity persistedEntity = entityCaptor.getValue();
        assertThat(persistedEntity.getStatus())
                .isEqualTo(TransactionStatus.REJECTED);
        assertThat(persistedEntity.getProviderStatus())
                .isEqualTo(ProviderStatus.REJECTED);
        assertThat(persistedEntity.getProviderCode())
                .isEqualTo(providerResult.code());
        assertThat(persistedEntity.getProviderMessage())
                .isEqualTo(providerResult.message());
        assertThat(persistedEntity.getProviderTransactionId()).isNull();
        assertThat(persistedEntity.getBalanceAfter()).isNull();
        assertThat(persistedEntity.getProviderExecutedAt()).isNull();
        assertThat(persistedEntity.getErrorMessage()).isNull();
        assertThat(result).isSameAs(repositoryResult);
    }

    @Test
    void rejectsNullTransactionWithoutCallingDependencies() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.execute(null));

        verifyNoInteractions(provider, repository);
    }

    @Test
    void propagatesProviderFailureWithoutRetryOrPersistence() {
        Transaction transaction = validTransaction();
        IllegalStateException providerFailure =
                new IllegalStateException("Provider unavailable");
        when(provider.execute(transaction)).thenThrow(providerFailure);

        assertThatThrownBy(() -> service.execute(transaction))
                .isSameAs(providerFailure);
        verify(provider, times(1)).execute(transaction);
        verifyNoInteractions(repository);
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
