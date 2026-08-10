package io.github.angtonmx.transactionprocessing.transaction.web;

import java.math.BigDecimal;

import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;

public record TransactionRequest(
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description) {

    Transaction toTransaction() {
        return new Transaction(
                accountId,
                type,
                amount,
                currency,
                description);
    }
}
