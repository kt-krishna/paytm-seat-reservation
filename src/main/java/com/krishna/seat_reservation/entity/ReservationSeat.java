package com.krishna.seat_reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reservation_seats")
@IdClass(ReservationSeat.ReservationSeatId.class)
public class ReservationSeat {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Id
    @Column(name = "show_seat_id", nullable = false)
    private Long showSeatId;

    public ReservationSeat() {
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public Long getShowSeatId() {
        return showSeatId;
    }

    public void setShowSeatId(Long showSeatId) {
        this.showSeatId = showSeatId;
    }

    public static class ReservationSeatId implements Serializable {

        private UUID reservationId;
        private Long showSeatId;

        public ReservationSeatId() {
        }

        public ReservationSeatId(UUID reservationId, Long showSeatId) {
            this.reservationId = reservationId;
            this.showSeatId = showSeatId;
        }

        public UUID getReservationId() {
            return reservationId;
        }

        public void setReservationId(UUID reservationId) {
            this.reservationId = reservationId;
        }

        public Long getShowSeatId() {
            return showSeatId;
        }

        public void setShowSeatId(Long showSeatId) {
            this.showSeatId = showSeatId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof ReservationSeatId that)) {
                return false;
            }

            return Objects.equals(reservationId, that.reservationId)
                    && Objects.equals(showSeatId, that.showSeatId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reservationId, showSeatId);
        }
    }
}