# Seat Reservation at Scale

A small Spring Boot service for reserving assigned seats for an event, with a focus on handling concurrent reservation requests safely.

The main goal of this project is to make sure that when multiple users try to reserve the same seat at the same time, the system never sells that seat twice.

## Tech Stack

- Java 17
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Maven
- Docker
- Spring Boot Actuator
- Prometheus metrics

## What the application does

The service supports:

- Creating a show with a list of seats
- Reserving one or more seats
- Preventing the same seat from being reserved twice
- Limiting the number of seats a user can reserve
- Idempotent reservation requests
- Cancelling an existing reservation
- Reusing seats after cancellation
- Checking the current status of all seats
- Health and readiness endpoints
- Prometheus metrics
- Request/correlation IDs in logs

For multi-seat reservations, I chose an **all-or-nothing** approach. If even one requested seat is unavailable, the complete reservation is rejected and none of the requested seats are reserved.

## API Endpoints

### Create a show

```http
POST /shows
```

Example:

```json
{
  "name": "Live Load Test",
  "seats": ["A1", "A2", "A3", "A4", "A5"],
  "pricePaise": 10000
}
```

### Reserve seats

```http
POST /shows/{showId}/reserve
```

Headers:

```text
X-User-Id: user-123
X-Request-Id: request-123
```

Request:

```json
{
  "seats": ["A1", "A2"],
  "idempotencyKey": "reservation-001"
}
```

The user ID is taken from the request header and is not accepted in the request body.

### Get show details

```http
GET /shows/{showId}
```

This returns the complete seat state along with the total, available, held and confirmed seat counts.

### Cancel reservation

```http
POST /reservations/{reservationId}/cancel
```

The reservation can only be cancelled by the user who created it.

## Concurrency handling

Concurrency was the main part of the implementation.

For seat reservation, the application does not simply read a seat as `AVAILABLE` and then update it later. Instead, the seat is claimed using an atomic database update that only succeeds when the seat is still available.

This means that if several requests try to reserve the same seat concurrently, only one request can successfully change its state from `AVAILABLE`.

I also use a per-user/show database lock to serialize reservation attempts from the same user. This helps enforce the per-user seat limit correctly when multiple requests from the same user arrive at the same time.

The database remains the final source of truth for reservation correctness.

## Idempotency

Every reservation request requires an `idempotencyKey`.

For the same user, show and idempotency key:

- The original reservation is returned if the request is retried with the same payload.
- A different payload using the same key is rejected.
- Concurrent requests using the same key return the same reservation instead of creating multiple reservations.

The request payload is also stored as a SHA-256 hash so that the original request can be compared with retries.

## Database

PostgreSQL is used as the primary datastore.

Flyway manages the database schema. The current migrations create:

- Shows
- Show seats
- Reservations
- Reservation seats
- Idempotency keys
- Per-user reservation locks

The application uses `ddl-auto=validate`, so Hibernate does not modify the production schema.

## Cancellation

Reservations can be explicitly cancelled.

When a confirmed reservation is cancelled:

1. The reservation status changes to `CANCELLED`.
2. The associated seats are changed back to `AVAILABLE`.
3. The same seats can then be reserved by another user.

Cancellation also checks ownership, so another user cannot cancel someone else's reservation.

## Observability

The application exposes Spring Boot Actuator endpoints.

Health:

```text
/actuator/health
```

Prometheus metrics:

```text
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

Each request also gets a request ID through `X-Request-Id`. If the client does not provide one, the application generates one.

## Running locally

### Prerequisites

- Java 17
- Docker Desktop
- PostgreSQL, if running without Docker

### Using Docker Compose

Create a `.env` file:

```text
POSTGRES_PASSWORD=your-password
```

Then run:

```bash
docker compose up --build
```

The application will be available at:

```text
http://localhost:8080
```

### Running with Maven

Set the required PostgreSQL configuration in `application.properties`, then run:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Deployment

The application is deployed on Render and uses PostgreSQL hosted on Neon.

Live application:

https://paytm-seat-reservation-2tdc.onrender.com

Health check:

https://paytm-seat-reservation-2tdc.onrender.com/actuator/health

Prometheus:

https://paytm-seat-reservation-2tdc.onrender.com/actuator/prometheus

## Testing

The project contains automated tests covering the main concurrency and correctness scenarios, including:

- Concurrent reservations by the same user
- Concurrent idempotent requests
- Seat conflicts
- Rollback of multi-seat reservations
- Cancellation and seat reuse
- Idempotency key reuse with a different request
- Missing user identity
- Invalid requests
- Cancellation ownership

The concurrency tests were run successfully with all tests passing.

I also manually tested concurrent requests against the application to verify that multiple requests attempting to reserve the same seat resulted in only one successful reservation.

## Authentication note

For this assignment, user identity is represented using the `X-User-Id` header.

This is intentionally lightweight for the scope of the exercise and is **not intended to represent production JWT/OAuth authentication**.

## Design choices

A few decisions were intentionally kept simple:

- PostgreSQL is the source of truth for seat state.
- Seat claiming is performed atomically at the database level.
- Multi-seat reservations are all-or-nothing.
- Explicit cancellation is supported.
- Idempotency is persisted in PostgreSQL rather than relying on in-memory state.
- Per-user reservation locking is handled at the database level.
- The service is stateless from the application perspective, allowing multiple application instances to share the same database.

## Repository

GitHub:

https://github.com/kt-krishna/paytm-seat-reservation