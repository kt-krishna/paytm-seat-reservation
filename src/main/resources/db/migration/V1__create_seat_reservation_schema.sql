-- ============================================================
-- V1: Seat Reservation Schema
-- ============================================================

-- Shows
CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    max_seats_per_user INTEGER NOT NULL DEFAULT 4
        CHECK (max_seats_per_user > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seats belonging to a show
CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    reservation_id UUID NULL,
    hold_expires_at TIMESTAMPTZ NULL,

    CONSTRAINT chk_show_seat_status
        CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),

    CONSTRAINT uq_show_seat
        UNIQUE (show_id, seat_number)
);

-- Reservations
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(255) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NULL,

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

-- Seats belonging to a reservation
CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    show_seat_id BIGINT NOT NULL REFERENCES show_seats(id),
    
    PRIMARY KEY (reservation_id, show_seat_id),

    CONSTRAINT uq_reservation_seat
        UNIQUE (show_seat_id)
);

-- Idempotency keys
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    show_id UUID NOT NULL REFERENCES shows(id),
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    reservation_id UUID NULL REFERENCES reservations(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_show_idempotency
        UNIQUE (user_id, show_id, idempotency_key)
);

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_show_seats_show_status
    ON show_seats(show_id, status);

CREATE INDEX idx_show_seats_reservation
    ON show_seats(reservation_id);

CREATE INDEX idx_reservations_show
    ON reservations(show_id);

CREATE INDEX idx_reservations_user
    ON reservations(user_id);

CREATE INDEX idx_reservations_show_user
    ON reservations(show_id, user_id);

CREATE INDEX idx_reservations_expires
    ON reservations(expires_at);

CREATE INDEX idx_idempotency_reservation
    ON idempotency_keys(reservation_id);