package io.github.angtonmx.transactionprocessing.transaction.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;

public interface TransactionRepository
        extends JpaRepository<TransactionEntity, UUID> {

    @Query("""
            SELECT transaction
            FROM TransactionEntity transaction
            WHERE (:accountId IS NULL OR transaction.accountId = :accountId)
              AND (:status IS NULL OR transaction.status = :status)
              AND (:type IS NULL OR transaction.type = :type)
            """)
    Page<TransactionEntity> findTransactions(
            @Param("accountId") String accountId,
            @Param("status") TransactionStatus status,
            @Param("type") TransactionType type,
            Pageable pageable);
}
