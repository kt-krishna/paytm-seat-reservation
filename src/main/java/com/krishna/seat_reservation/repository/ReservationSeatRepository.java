package com.krishna.seat_reservation.repository;

import com.krishna.seat_reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, ReservationSeat.ReservationSeatId> {

    List<ReservationSeat> findByReservationId(UUID reservationId);
}