package io.github.angtonmx.transactionprocessing.transaction;

public final class TransactionService {

    private final TransactionProvider provider;

    public TransactionService(TransactionProvider provider) {
        this.provider = provider;
    }

    public ProviderResult execute(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }

        return provider.execute(transaction);
    }
}
