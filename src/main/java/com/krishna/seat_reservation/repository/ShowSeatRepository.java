package com.krishna.seat_reservation.repository;

import com.krishna.seat_reservation.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
	@Query("""
		    SELECT s
		    FROM ShowSeat s
		    WHERE s.showId = :showId
		      AND s.seatNumber IN :seatNumbers
		""")
		List<ShowSeat> findByShowIdAndSeatNumberIn(
		        @Param("showId") UUID showId,
		        @Param("seatNumbers") List<String> seatNumbers);

    List<ShowSeat> findByShowIdOrderBySeatNumber(UUID showId);
    
    @Modifying
    @Query("""
        UPDATE ShowSeat s
        SET s.status = 'AVAILABLE',
            s.reservationId = NULL,
            s.holdExpiresAt = NULL
        WHERE s.reservationId = :reservationId
          AND s.status = 'CONFIRMED'
    """)
    int releaseSeatsByReservationId(
        @Param("reservationId") UUID reservationId
    );

    @Modifying
    @Query(value = """
            UPDATE show_seats
            SET status = 'CONFIRMED',
                reservation_id = :reservationId
            WHERE show_id = :showId
              AND seat_number = :seatNumber
              AND status = 'AVAILABLE'
            """, nativeQuery = true)
    int claimSeat(
            @Param("showId") UUID showId,
            @Param("seatNumber") String seatNumber,
            @Param("reservationId") UUID reservationId
    );
}