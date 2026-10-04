package com.krishna.seat_reservation.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.krishna.seat_reservation.dto.ReservationResponse;
import com.krishna.seat_reservation.dto.ReserveSeatsRequest;
import com.krishna.seat_reservation.entity.Reservation;
import com.krishna.seat_reservation.exception.ResourceNotFoundException;
import com.krishna.seat_reservation.exception.SeatReservationException;
import com.krishna.seat_reservation.metrics.ReservationMetrics;
import com.krishna.seat_reservation.repository.IdempotencyKeyRepository;
import com.krishna.seat_reservation.repository.ReservationRepository;
import com.krishna.seat_reservation.repository.ReservationSeatRepository;
import com.krishna.seat_reservation.repository.ReservationUserLockRepository;
import com.krishna.seat_reservation.repository.ShowRepository;
import com.krishna.seat_reservation.repository.ShowSeatRepository;

@Service
public class ReservationService {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ReservationUserLockRepository reservationUserLockRepository;
    private final ReservationMetrics reservationMetrics;

    public ReservationService(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository,
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            ReservationUserLockRepository reservationUserLockRepository,
            ReservationMetrics reservationMetrics) {

        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.reservationUserLockRepository = reservationUserLockRepository;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public ReservationResponse reserve(
            UUID showId,
            String userId,
            ReserveSeatsRequest request) {
    	reservationMetrics.incrementAttempts();

        if (userId == null || userId.isBlank()) {
            throw new SeatReservationException("User identity is required");
        }

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new SeatReservationException("At least one seat is required");
        }

        if (request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank()) {
            throw new SeatReservationException("Idempotency key is required");
        }

        // Load show
        var show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Show not found: " + showId));

        log.info(
                "reservation_started showId={} userId={} idempotencyKey={} seatCount={}",
                showId,
                userId,
                request.getIdempotencyKey(),
                request.getSeats().size());

        // Normalize and sort seats.
        List<String> seats = request.getSeats()
                .stream()
                .map(String::trim)
                .sorted()
                .toList();

        // Reject duplicate seats.
        if (seats.stream().distinct().count() != seats.size()) {
            throw new SeatReservationException(
                    "Duplicate seats are not allowed");
        }

        // Ensure the lock row exists without raising duplicate-key error.
        reservationUserLockRepository.createIfAbsent(
                showId,
                userId,
                OffsetDateTime.now());

        // Lock this user + show row for duration of transaction.
        reservationUserLockRepository.findForUpdate(
                showId,
                userId);


        // Check idempotency AFTER acquiring the lock.
        var existingKey =
                idempotencyKeyRepository
                        .findByUserIdAndShowIdAndIdempotencyKey(
                                userId,
                                showId,
                                request.getIdempotencyKey());

        if (existingKey.isPresent()) {

            var key = existingKey.get();

            String requestHash = String.join(",", seats);

            if (!key.getRequestHash().equals(requestHash)) {
                throw new SeatReservationException(
                        "Idempotency key was already used with a different request");
            }

            if (key.getReservationId() == null) {
                throw new SeatReservationException(
                        "Reservation is currently being processed");
            }
            reservationMetrics.incrementIdempotentReplays();


            log.info(
                    "reservation_idempotent_replay showId={} userId={} reservationId={} idempotencyKey={}",
                    showId,
                    userId,
                    key.getReservationId(),
                    request.getIdempotencyKey());

            return buildResponse(
                    reservationRepository.findById(key.getReservationId())
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Reservation not found")));
        }

        // Now check the per-user limit.
        long existingSeats =
                reservationRepository
                        .countSeatsByShowIdAndUserIdAndStatus(
                                showId,
                                userId,
                                Reservation.ReservationStatus.CONFIRMED);

        if (existingSeats + seats.size() > show.getMaxSeatsPerUser()) {
            throw new SeatReservationException(
                    "Per-user seat limit exceeded");
        }

        UUID reservationId = UUID.randomUUID();

        // Create reservation.
        Reservation reservation = new Reservation();

        reservation.setId(reservationId);
        reservation.setShowId(showId);
        reservation.setUserId(userId);
        reservation.setAmountPaise(
                show.getPricePaise() * seats.size());
        reservation.setStatus(
                Reservation.ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(OffsetDateTime.now());

        // Atomic seat claims.
        for (String seatNumber : seats) {

            int updated = showSeatRepository.claimSeat(
                    showId,
                    seatNumber,
                    reservationId);

            if (updated == 0) {
                throw new SeatReservationException(
                        "Seat is already taken or does not exist: "
                                + seatNumber);
            }
        }

        reservationRepository.save(reservation);

     // Store reservation-seat mappings.
        List<com.krishna.seat_reservation.entity.ReservationSeat>
                reservationSeats = new ArrayList<>();

        var showSeats = showSeatRepository.findByShowIdAndSeatNumberIn(
                showId,
                seats);

        var showSeatsByNumber = showSeats.stream()
                .collect(java.util.stream.Collectors.toMap(
                        s -> s.getSeatNumber(),
                        s -> s));

        for (String seatNumber : seats) {

            var showSeat = showSeatsByNumber.get(seatNumber);

            if (showSeat == null) {
                throw new ResourceNotFoundException(
                        "Seat not found: " + seatNumber);
            }

            var reservationSeat =
                    new com.krishna.seat_reservation.entity.ReservationSeat();

            reservationSeat.setReservationId(reservationId);
            reservationSeat.setShowSeatId(showSeat.getId());

            reservationSeats.add(reservationSeat);
        }

        reservationSeatRepository.saveAll(reservationSeats);

        // Store idempotency record.
        var idempotencyKey =
                new com.krishna.seat_reservation.entity.IdempotencyKey();

        idempotencyKey.setUserId(userId);
        idempotencyKey.setShowId(showId);
        idempotencyKey.setIdempotencyKey(
                request.getIdempotencyKey());
        idempotencyKey.setRequestHash(
                String.join(",", seats));
        idempotencyKey.setReservationId(reservationId);
        idempotencyKey.setCreatedAt(OffsetDateTime.now());

        idempotencyKeyRepository.save(idempotencyKey);
        reservationMetrics.incrementSuccess();

        log.info(
                "reservation_created showId={} userId={} reservationId={} seats={} amountPaise={}",
                showId,
                userId,
                reservationId,
                seats,
                reservation.getAmountPaise());

        return buildResponse(reservation);
    }

    private ReservationResponse buildResponse(
            Reservation reservation) {

        List<String> seats =
                reservationSeatRepository
                        .findByReservationId(reservation.getId())
                        .stream()
                        .map(mapping ->
                                showSeatRepository.findById(
                                                mapping.getShowSeatId())
                                        .orElseThrow()
                                        .getSeatNumber())
                        .sorted()
                        .toList();

        ReservationResponse response =
                new ReservationResponse();

        response.setReservationId(reservation.getId());
        response.setShowId(reservation.getShowId());
        response.setUserId(reservation.getUserId());
        response.setSeats(seats);
        response.setAmountPaise(reservation.getAmountPaise());
        response.setStatus(
                reservation.getStatus().name().toLowerCase());

        return response;
    }

    @Transactional
    public ReservationResponse cancel(UUID reservationId, String userId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Reservation not found"));

        log.info(
                "reservation_cancellation_requested reservationId={} userId={} owner={}",
                reservationId,
                userId,
                reservation.getUserId());

        if (!reservation.getUserId().equals(userId)) {
            throw new SeatReservationException(
                    "You are not allowed to cancel this reservation");
        }

        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {
            return buildResponse(reservation);
        }

        if (reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
            throw new SeatReservationException(
                    "Only confirmed reservations can be cancelled");
        }

        reservationRepository.cancelReservation(
                reservationId,
                Reservation.ReservationStatus.CANCELLED);

        showSeatRepository.releaseSeatsByReservationId(reservationId);

        reservationMetrics.incrementCancellations();

        reservation.setStatus(Reservation.ReservationStatus.CANCELLED);

        log.info(
                "reservation_cancelled reservationId={} userId={}",
                reservationId,
                reservation.getUserId());

        return buildResponse(reservation);
    }
}