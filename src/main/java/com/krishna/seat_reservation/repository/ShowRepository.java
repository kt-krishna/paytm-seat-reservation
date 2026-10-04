package com.krishna.seat_reservation.repository;

import com.krishna.seat_reservation.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {
}