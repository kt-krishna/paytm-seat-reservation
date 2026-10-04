package com.krishna.seat_reservation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.krishna.seat_reservation.dto.ReservationResponse;
import com.krishna.seat_reservation.dto.ReserveSeatsRequest;
import com.krishna.seat_reservation.service.ReservationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(
            ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserve(
            @PathVariable UUID showId,
            @RequestHeader(value = "X-User-Id", required = false)
            String userId,
            @Valid @RequestBody ReserveSeatsRequest request) {

        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "X-User-Id header is required"
            );
        }

        ReservationResponse response =
                reservationService.reserve(
                        showId,
                        userId,
                        request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID reservationId,
            @RequestHeader(value = "X-User-Id", required = false)
            String userId) {

        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "X-User-Id header is required"
            );
        }

        ReservationResponse response =
                reservationService.cancel(
                        reservationId,
                        userId);

        return ResponseEntity.ok(response);
    }
}