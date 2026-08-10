package io.github.angtonmx.transactionprocessing.transaction.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository
        extends JpaRepository<TransactionEntity, UUID> {
}
