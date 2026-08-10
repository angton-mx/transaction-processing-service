package io.github.angtonmx.transactionprocessing.transaction;

import java.math.BigDecimal;

public record Transaction(
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description) {

    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_DEBIT_AMOUNT = new BigDecimal("10000.00");
    private static final String SUPPORTED_CURRENCY = "MXN";

    public Transaction {
        validateAccountId(accountId);
        validateType(type);
        validateAmount(amount);
        validateDebitAmount(type, amount);
        validateCurrency(currency);
    }

    private static void validateAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required");
        }
    }

    private static void validateType(TransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Transaction amount is required");
        }

        if (amount.compareTo(MINIMUM_AMOUNT) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be greater than 1.00");
        }
    }

    private static void validateDebitAmount(TransactionType type, BigDecimal amount) {
        if (type == TransactionType.DEBIT && amount.compareTo(MAX_DEBIT_AMOUNT) > 0) {
            throw new IllegalArgumentException("Debit amount must not exceed 10000.00");
        }
    }

    private static void validateCurrency(String currency) {
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw new IllegalArgumentException("Only MXN currency is supported");
        }
    }
}
