package io.github.angtonmx.transactionprocessing.transaction.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;
import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionEntity;

public record TransactionResponse(
        UUID id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String providerTransactionId,
        BigDecimal balanceAfter,
        Instant createdAt) {

    static TransactionResponse from(TransactionEntity entity) {
        return new TransactionResponse(
                entity.getId(),
                entity.getAccountId(),
                entity.getType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getProviderTransactionId(),
                entity.getBalanceAfter(),
                entity.getCreatedAt());
    }
}
