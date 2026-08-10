package io.github.angtonmx.transactionprocessing.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionEntity;
import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionRepository;

public final class TransactionService {

    private final TransactionProvider provider;
    private final TransactionRepository repository;

    public TransactionService(
            TransactionProvider provider,
            TransactionRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }

    public TransactionEntity execute(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }

        ProviderResult providerResult = provider.execute(transaction);
        TransactionEntity entity = switch (providerResult.status()) {
            case APPROVED -> TransactionEntity.executed(transaction, providerResult);
            case REJECTED -> TransactionEntity.rejected(transaction, providerResult);
        };
        return repository.save(entity);
    }

    public Page<TransactionEntity> find(
            String accountId,
            TransactionStatus status,
            TransactionType type,
            int page,
            int limit) {
        Sort order = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));
        return repository.findTransactions(
                accountId,
                status,
                type,
                PageRequest.of(page, limit, order));
    }
}
