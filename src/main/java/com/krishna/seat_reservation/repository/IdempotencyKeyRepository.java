package com.krishna.seat_reservation.repository;

import com.krishna.seat_reservation.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndShowIdAndIdempotencyKey(
            String userId,
            UUID showId,
            String idempotencyKey
    );
}