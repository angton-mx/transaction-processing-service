package io.github.angtonmx.transactionprocessing.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderResult(
        String providerTransactionId,
        ProviderStatus status,
        BigDecimal balance,
        Instant executedAt,
        String code,
        String message) {
}
