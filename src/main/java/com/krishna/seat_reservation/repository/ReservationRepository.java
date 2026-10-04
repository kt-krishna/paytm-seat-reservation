package com.krishna.seat_reservation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.krishna.seat_reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
	@Modifying
	@Query("""
	    UPDATE Reservation r
	    SET r.status = :status
	    WHERE r.id = :reservationId
	      AND r.status = 'CONFIRMED'
	""")
	int cancelReservation(
		    @Param("reservationId") UUID reservationId,
		    @Param("status") Reservation.ReservationStatus status
		);

	@Query("""
		    SELECT COUNT(rs.showSeatId)
		    FROM ReservationSeat rs
		    JOIN Reservation r ON r.id = rs.reservationId
		    WHERE r.showId = :showId
		      AND r.userId = :userId
		      AND r.status = :status
		""")
		long countSeatsByShowIdAndUserIdAndStatus(
		        @Param("showId") UUID showId,
		        @Param("userId") String userId,
		        @Param("status") Reservation.ReservationStatus status
		);
	
}