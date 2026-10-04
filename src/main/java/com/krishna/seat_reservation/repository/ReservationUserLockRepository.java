package com.krishna.seat_reservation.repository;

import com.krishna.seat_reservation.entity.ReservationUserLock;
import com.krishna.seat_reservation.entity.ReservationUserLock.ReservationUserLockId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReservationUserLockRepository
        extends JpaRepository<ReservationUserLock, ReservationUserLockId> {

    @Modifying
    @Query(value = """
            INSERT INTO reservation_user_locks
                (show_id, user_id, created_at)
            VALUES
                (:showId, :userId, :createdAt)
            ON CONFLICT (show_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Query(value = """
            SELECT *
            FROM reservation_user_locks
            WHERE show_id = :showId
              AND user_id = :userId
            FOR UPDATE
            """, nativeQuery = true)
    ReservationUserLock findForUpdate(
            @Param("showId") UUID showId,
            @Param("userId") String userId
    );
}