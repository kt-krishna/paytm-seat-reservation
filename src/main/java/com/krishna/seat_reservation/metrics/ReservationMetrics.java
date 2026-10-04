package com.krishna.seat_reservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final Counter reservationAttempts;
    private final Counter reservationSuccess;
    private final Counter reservationConflicts;
    private final Counter reservationIdempotentReplays;
    private final Counter reservationCancellations;

    public ReservationMetrics(MeterRegistry meterRegistry) {

        reservationAttempts = Counter.builder("reservation_attempts_total")
                .description("Total number of reservation attempts")
                .register(meterRegistry);

        reservationSuccess = Counter.builder("reservation_success_total")
                .description("Total number of successful reservations")
                .register(meterRegistry);

        reservationConflicts = Counter.builder("reservation_conflicts_total")
                .description("Total number of reservation conflicts")
                .register(meterRegistry);

        reservationIdempotentReplays = Counter.builder("reservation_idempotent_replays_total")
                .description("Total number of idempotent reservation replays")
                .register(meterRegistry);

        reservationCancellations = Counter.builder("reservation_cancellations_total")
                .description("Total number of reservation cancellations")
                .register(meterRegistry);
    }

    public void incrementAttempts() {
        reservationAttempts.increment();
    }

    public void incrementSuccess() {
        reservationSuccess.increment();
    }

    public void incrementConflicts() {
        reservationConflicts.increment();
    }

    public void incrementIdempotentReplays() {
        reservationIdempotentReplays.increment();
    }

    public void incrementCancellations() {
        reservationCancellations.increment();
    }
}