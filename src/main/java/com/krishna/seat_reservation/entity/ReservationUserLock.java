package com.krishna.seat_reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reservation_user_locks")
@IdClass(ReservationUserLock.ReservationUserLockId.class)
public class ReservationUserLock {

    @Id
    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ReservationUserLock() {
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public static class ReservationUserLockId implements Serializable {

        private UUID showId;
        private String userId;

        public ReservationUserLockId() {
        }

        public ReservationUserLockId(UUID showId, String userId) {
            this.showId = showId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReservationUserLockId that)) return false;
            return Objects.equals(showId, that.showId)
                    && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(showId, userId);
        }
    }
}