# Seat Reservation at Scale — Design Write-up

## 1. Overview

This project implements a seat reservation service using Java, Spring Boot and PostgreSQL.

The main challenge was not creating the REST APIs, but making the reservation flow safe when many users try to reserve the same seats at the same time.

The implementation therefore keeps the database as the source of truth and uses database-level operations to protect seat ownership.

The application is deployed on Render with PostgreSQL hosted on Neon.

Live URL:

https://paytm-seat-reservation-2tdc.onrender.com

---

## 2. Main Design

The main entities are:

- `shows` — stores show information and pricing.
- `show_seats` — stores every seat and its current state.
- `reservations` — stores reservation information.
- `reservation_seats` — maps reservations to seats.
- `idempotency_keys` — stores idempotency information for retries.
- `reservation_user_locks` — provides per-user/show locking during concurrent reservations.

A seat can be in one of these states:

```text
AVAILABLE
HELD
CONFIRMED
```

For the current implementation, reservations are confirmed immediately, so the normal reservation flow moves a seat from `AVAILABLE` to `CONFIRMED`.

Cancellation moves it back to `AVAILABLE`.

---

## 3. Handling Concurrent Reservations

The most important correctness requirement is that two users must never successfully reserve the same seat.

A simple approach would be:

1. Read the seat.
2. Check whether it is available.
3. Update the seat.

The problem with this approach is that two requests can read the same available seat before either request updates it.

To avoid this race condition, the application uses an atomic database update.

Conceptually, the operation is:

```sql
UPDATE show_seats
SET status = 'CONFIRMED',
    reservation_id = ?
WHERE show_id = ?
  AND seat_number = ?
  AND status = 'AVAILABLE';
```

The application checks the number of affected rows.

If the update affects one row, the seat was successfully claimed.

If it affects zero rows, another request already claimed the seat, so the reservation is rejected with HTTP 409.

This makes the database operation itself responsible for the final seat ownership decision.

---

## 4. Per-User Reservation Limit

The default maximum number of confirmed seats per user is 4.

The system also has a `reservation_user_locks` table.

For a reservation, the application creates the `(show_id, user_id)` lock row if necessary and then locks that row using a database `FOR UPDATE`.

This serializes reservation attempts for the same user and show.

This is important because simply checking the current reservation count is not sufficient when multiple requests from the same user arrive concurrently.

For example, without locking:

```text
Request 1 -> sees 3 seats
Request 2 -> sees 3 seats
Request 1 -> adds 1
Request 2 -> adds 1
```

Both requests could incorrectly succeed.

The database lock prevents this race.

---

## 5. Idempotency

Reservation requests require an idempotency key.

The key is stored together with:

- User ID
- Show ID
- Idempotency key
- Request hash
- Reservation ID

The database has a unique constraint on:

```text
(user_id, show_id, idempotency_key)
```

When the same request is retried, the existing reservation is returned instead of creating another reservation.

If the same idempotency key is used with a different request body, the request is rejected.

The request is normalized before calculating a SHA-256 hash so that the same set of requested seats can be compared consistently.

This also makes retry behavior safe if the client does not know whether the first request succeeded because of a network failure.

---

## 6. Multi-Seat Reservation

I chose an all-or-nothing approach for multi-seat reservations.

For example, if a user requests:

```text
A1, A2, A3
```

and `A2` is already taken, the entire reservation fails.

The application runs the reservation inside a transaction. If one of the requested seats cannot be claimed, an exception is raised and the transaction is rolled back.

Therefore, the system does not leave the user with only a partial reservation.

This also keeps the seat counts consistent.

---

## 7. Cancellation and Seat Reuse

The service supports explicit cancellation.

When a confirmed reservation is cancelled:

1. The reservation is changed to `CANCELLED`.
2. The associated seats are changed back to `AVAILABLE`.
3. The seats can be reserved again.

Cancellation also checks the user identity.

A user cannot cancel a reservation belonging to another user.

The database schema was adjusted so that a previously cancelled seat can be associated with a new reservation later.

---

## 8. Database and Transactions

PostgreSQL is used because the reservation correctness depends heavily on database transactions, constraints and atomic updates.

Flyway is used for schema management.

The schema is created through versioned migrations:

```text
V1 - create seat reservation schema
V2 - add reservation user locks
V3 - allow reuse of cancelled seats
```

Hibernate is configured with:

```text
spring.jpa.hibernate.ddl-auto=validate
```

This means Hibernate validates the schema instead of changing it.

The application therefore keeps schema changes under Flyway control.

---

## 9. Observability

Spring Boot Actuator and Micrometer are used for application monitoring.

The application exposes:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Reservation-specific metrics include:

```text
reservation_attempts_total
reservation_success_total
reservation_conflicts_total
reservation_idempotent_replays_total
reservation_cancellations_total
```

Spring Boot also exposes JVM, HTTP, database connection pool and application metrics through Prometheus.

The live deployment was verified using the production health endpoint and Prometheus endpoint.

---

## 10. Request Tracing

Each request can contain:

```text
X-Request-Id
```

If the client does not provide one, the application generates a UUID.

The request ID is added to the logging MDC and is also returned in the response.

This makes it easier to trace a particular request through the application logs.

---

## 11. Authentication

For this assignment, user identity is represented using:

```text
X-User-Id
```

The reservation API does not accept the user ID as part of the request body.

This keeps the reservation ownership tied to the request identity rather than allowing a client to submit an arbitrary user ID in the reservation payload.

This is intentionally a lightweight authentication mechanism for the assignment and is not intended to represent a production JWT/OAuth authentication system.

---

## 12. Testing

Automated tests cover the main correctness scenarios.

These include:

- Per-user seat limit under concurrency
- Concurrent idempotent requests
- Seat conflict handling
- Rollback when one seat in a multi-seat reservation is unavailable
- Cancellation
- Seat reuse after cancellation
- Reusing an idempotency key with a different request
- Missing user identity
- Invalid reservation requests
- Cancellation ownership

The test suite completed successfully with all tests passing.

I also manually tested concurrent requests against the application.

For a hot-seat test with 20 concurrent requests attempting to reserve the same seat, only one request was allowed to claim that particular seat while the other requests received conflict responses.

Concurrent requests using the same idempotency key returned the same reservation ID rather than creating multiple reservations.

---

## 13. Load and Scalability Considerations

The assignment is designed around a high-concurrency scenario with approximately 20,000 concurrent reservation attempts.

The implementation avoids using in-memory locks as the source of truth because that would become problematic if multiple application instances were running.

Instead:

- Seat ownership is protected by an atomic database update.
- Idempotency is persisted in PostgreSQL.
- Per-user serialization uses a database lock.
- Application state does not need to be shared between application instances.

This allows multiple application instances to operate against the same database without relying on JVM-local synchronization for correctness.

The database connection pool and indexes are also used to keep the reservation queries bounded.

Important indexes include:

```text
(show_id, seat_number)
(show_id, status)
(show_id, user_id)
(expires_at)
```

---

## 14. Deployment

The application is containerized using Docker.

The Dockerfile uses a multi-stage build:

```text
Maven + JDK 17
        |
        v
Build Spring Boot JAR
        |
        v
JRE 17 runtime image
```

The application is deployed on Render.

PostgreSQL is hosted using Neon.

The production deployment was verified with:

```text
/actuator/health
/actuator/prometheus
```

Flyway migrations were also successfully applied to the production database during deployment.

---

## 15. Trade-offs

A few areas were intentionally kept simple because this is a take-home assignment rather than a complete production ticketing platform.

### Lightweight identity

`X-User-Id` is used instead of implementing a complete authentication provider.

### Immediate confirmation

The current implementation confirms reservations immediately rather than introducing a separate payment/checkout flow.

### Explicit cancellation

The current flow supports explicit cancellation. A separate payment timeout/automatic expiry workflow was not added because it was not required for the implemented reservation flow.

### Database-based coordination

PostgreSQL is deliberately used for correctness instead of introducing Redis or another distributed locking system.

This keeps the system smaller while still allowing multiple application instances to share the same source of truth.

---

## 16. What I Would Improve Next

If this were taken further into a production system, I would consider:

- JWT/OAuth authentication
- Admin authentication for show creation
- Automatic hold expiry
- Redis for high-volume caching and coordination where appropriate
- More detailed reservation latency metrics
- Seat-availability gauges
- JSON structured logging
- Distributed tracing
- Dedicated load-testing infrastructure
- Database partitioning/sharding if the dataset and traffic required it
- Queue-based processing for non-critical asynchronous operations

The current implementation intentionally focuses first on reservation correctness, concurrency safety and idempotency.

---

## 17. Conclusion

The main design principle of this implementation is that reservation correctness should not depend on application timing.

Multiple requests may arrive at almost exactly the same time, but the database must still have a single authoritative answer about who owns a seat.

Atomic seat claiming, database transactions, per-user locking and persisted idempotency together provide that protection.

The application is containerized, deployed publicly, connected to a managed PostgreSQL database, and exposes health and Prometheus endpoints for basic operational visibility.