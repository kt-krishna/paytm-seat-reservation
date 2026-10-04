-- ============================================================
-- V2: Concurrency-safe per-user reservation locking
-- ============================================================

CREATE TABLE reservation_user_locks (
    show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (show_id, user_id)
);