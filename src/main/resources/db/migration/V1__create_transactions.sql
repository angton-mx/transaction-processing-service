CREATE TABLE transactions (
    id UUID PRIMARY KEY,

    account_id TEXT NOT NULL,
    type TEXT NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT,

    status TEXT NOT NULL,
    provider_status TEXT,

    provider_transaction_id TEXT,
    balance_after NUMERIC,
    provider_executed_at TIMESTAMPTZ,

    provider_code TEXT,
    provider_message TEXT,
    error_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transactions_account_id
        CHECK (btrim(account_id) <> ''),

    CONSTRAINT chk_transactions_type
        CHECK (type IN ('CREDIT', 'DEBIT')),

    CONSTRAINT chk_transactions_amount
        CHECK (amount > 1.00),

    CONSTRAINT chk_transactions_debit_limit
        CHECK (
            type <> 'DEBIT'
            OR amount <= 10000.00
        ),

    CONSTRAINT chk_transactions_currency
        CHECK (currency = 'MXN'),

    CONSTRAINT chk_transactions_status
        CHECK (
            status IN ('EXECUTED', 'REJECTED', 'FAILED')
        ),

    CONSTRAINT chk_transactions_provider_status
        CHECK (
            provider_status IS NULL
            OR provider_status IN ('APPROVED', 'REJECTED')
        ),

    CONSTRAINT chk_transactions_result
        CHECK (
            (
                status = 'EXECUTED'
                AND provider_status IS NOT NULL
                AND provider_status = 'APPROVED'
                AND provider_transaction_id IS NOT NULL
                AND balance_after IS NOT NULL
                AND provider_executed_at IS NOT NULL
                AND provider_code IS NULL
                AND provider_message IS NULL
                AND error_message IS NULL
            )
            OR
            (
                status = 'REJECTED'
                AND provider_status IS NOT NULL
                AND provider_status = 'REJECTED'
                AND provider_transaction_id IS NULL
                AND balance_after IS NULL
                AND provider_executed_at IS NULL
                AND provider_code IS NOT NULL
                AND provider_message IS NOT NULL
                AND error_message IS NULL
            )
            OR
            (
                status = 'FAILED'
                AND provider_status IS NULL
                AND provider_transaction_id IS NULL
                AND balance_after IS NULL
                AND provider_executed_at IS NULL
                AND provider_code IS NULL
                AND provider_message IS NULL
                AND error_message IS NOT NULL
            )
        )
);

CREATE INDEX idx_transactions_created
    ON transactions (created_at DESC, id DESC);

CREATE INDEX idx_transactions_account_created
    ON transactions (account_id, created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_transactions_provider_id
    ON transactions (provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
