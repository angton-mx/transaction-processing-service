package io.github.angtonmx.transactionprocessing.transaction;

public interface TransactionProvider {

    ProviderResult execute(Transaction transaction);
}
