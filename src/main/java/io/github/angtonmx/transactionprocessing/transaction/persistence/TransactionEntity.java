package io.github.angtonmx.transactionprocessing.transaction.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import io.github.angtonmx.transactionprocessing.transaction.ProviderResult;
import io.github.angtonmx.transactionprocessing.transaction.ProviderStatus;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status")
    private ProviderStatus providerStatus;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "balance_after")
    private BigDecimal balanceAfter;

    @Column(name = "provider_executed_at")
    private Instant providerExecutedAt;

    @Column(name = "provider_code")
    private String providerCode;

    @Column(name = "provider_message")
    private String providerMessage;

    @Column(name = "error_message")
    private String errorMessage;

    @Generated(event = EventType.INSERT)
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false)
    private Instant createdAt;

    protected TransactionEntity() {
    }

    private TransactionEntity(Transaction transaction, TransactionStatus status) {
        this.accountId = transaction.accountId();
        this.type = transaction.type();
        this.amount = transaction.amount();
        this.currency = transaction.currency();
        this.description = transaction.description();
        this.status = status;
    }

    public static TransactionEntity executed(
            Transaction transaction,
            ProviderResult providerResult) {
        TransactionEntity entity = new TransactionEntity(
                transaction,
                TransactionStatus.EXECUTED);
        entity.providerStatus = providerResult.status();
        entity.providerTransactionId = providerResult.providerTransactionId();
        entity.balanceAfter = providerResult.balance();
        entity.providerExecutedAt = providerResult.executedAt();
        return entity;
    }

    public static TransactionEntity rejected(
            Transaction transaction,
            ProviderResult providerResult) {
        TransactionEntity entity = new TransactionEntity(
                transaction,
                TransactionStatus.REJECTED);
        entity.providerStatus = providerResult.status();
        entity.providerCode = providerResult.code();
        entity.providerMessage = providerResult.message();
        return entity;
    }

    public static TransactionEntity failed(
            Transaction transaction,
            String errorMessage) {
        TransactionEntity entity = new TransactionEntity(
                transaction,
                TransactionStatus.FAILED);
        entity.errorMessage = errorMessage;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public ProviderStatus getProviderStatus() {
        return providerStatus;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getProviderExecutedAt() {
        return providerExecutedAt;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getProviderMessage() {
        return providerMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
