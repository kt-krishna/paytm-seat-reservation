package com.krishna.seat_reservation;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationConcurrencyTest {

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void shouldEnforcePerUserSeatLimitUnderConcurrency() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10"));

		String url = "http://localhost:8080/shows/" + showId + "/reserve";

		int concurrentRequests = 10;

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		AtomicInteger unexpectedCount = new AtomicInteger();

		List<CompletableFuture<Void>> requests = new ArrayList<>();

		for (int i = 1; i <= concurrentRequests; i++) {

			String seat = "A" + i;

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Content-Type", "application/json").header("X-User-Id", "same-concurrent-user")
					.POST(HttpRequest.BodyPublishers.ofString("""
							{
							  "seats": ["%s"],
							  "idempotencyKey": "%s"
							}
							""".formatted(seat, UUID.randomUUID()))).build();

			requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {

				if (response.statusCode() == 201) {
					successCount.incrementAndGet();
				} else if (response.statusCode() == 409) {
					conflictCount.incrementAndGet();
				} else {
					unexpectedCount.incrementAndGet();
					System.out.println("Unexpected response: " + response.statusCode() + " " + response.body());
				}
			}));
		}

		CompletableFuture.allOf(requests.toArray(new CompletableFuture[0])).join();

		System.out.println("Success    : " + successCount.get());
		System.out.println("Conflicts  : " + conflictCount.get());
		System.out.println("Unexpected : " + unexpectedCount.get());

		assertEquals(4, successCount.get());
		assertEquals(6, conflictCount.get());
		assertEquals(0, unexpectedCount.get());
	}

	@Test
	void shouldReturnSameReservationForConcurrentIdempotentRequests() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2", "A3"));

		String url = "http://localhost:8080/shows/" + showId + "/reserve";

		String idempotencyKey = UUID.randomUUID().toString();

		int concurrentRequests = 10;

		List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();

		for (int i = 0; i < concurrentRequests; i++) {

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Content-Type", "application/json").header("X-User-Id", "idempotency-test-user")
					.POST(HttpRequest.BodyPublishers.ofString("""
							{
							  "seats": ["A1"],
							  "idempotencyKey": "%s"
							}
							""".formatted(idempotencyKey))).build();

			requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
		}

		List<HttpResponse<String>> responses = CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]))
				.thenApply(v -> requests.stream().map(CompletableFuture::join).toList()).join();

		long successCount = responses.stream().filter(r -> r.statusCode() == 201).count();

		long conflictCount = responses.stream().filter(r -> r.statusCode() == 409).count();

		long unexpectedCount = responses.stream().filter(r -> r.statusCode() != 201 && r.statusCode() != 409).count();

		System.out.println("Success    : " + successCount);
		System.out.println("Conflicts  : " + conflictCount);
		System.out.println("Unexpected : " + unexpectedCount);

		assertEquals(10, successCount);
		assertEquals(0, conflictCount);
		assertEquals(0, unexpectedCount);

		String firstReservationId = responses.get(0).body().split("\"reservationId\":\"")[1].split("\"")[0];

		for (HttpResponse<String> response : responses) {

			String reservationId = response.body().split("\"reservationId\":\"")[1].split("\"")[0];

			assertEquals(firstReservationId, reservationId);
		}
	}

	@Test
	void shouldRollbackAllSeatsWhenOneRequestedSeatIsUnavailable() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2", "A3"));

		reserve(showId, "first-user", List.of("A2"));

		HttpResponse<String> response = reserve(showId, "second-user", List.of("A1", "A2"));

		assertEquals(409, response.statusCode());

		HttpResponse<String> showResponse = client.send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/shows/" + showId)).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertEquals(200, showResponse.statusCode());

		String body = showResponse.body();

		System.out.println("Show : " + body);

		assert body.contains("\"seatNumber\":\"A1\",\"status\":\"AVAILABLE\"");

		assert body.contains("\"seatNumber\":\"A2\",\"status\":\"CONFIRMED\"");

		assert body.contains("\"seatNumber\":\"A3\",\"status\":\"AVAILABLE\"");
	}

	@Test
	void shouldReleaseSeatAfterCancellation() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2"));

		HttpResponse<String> reserveResponse = reserve(showId, "cancel-test-user", List.of("A1"));

		assertEquals(201, reserveResponse.statusCode());

		String reservationId = reserveResponse.body().split("\"reservationId\":\"")[1].split("\"")[0];

		HttpRequest cancelRequest = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/shows/reservations/" + reservationId + "/cancel"))
				.POST(HttpRequest.BodyPublishers.noBody()).build();

		HttpResponse<String> cancelResponse = client.send(cancelRequest, HttpResponse.BodyHandlers.ofString());

		System.out.println("Cancel Status : " + cancelResponse.statusCode());

		System.out.println("Cancel Body   : " + cancelResponse.body());

		assertEquals(200, cancelResponse.statusCode());

		HttpResponse<String> secondReserveResponse = reserve(showId, "new-user", List.of("A1"));

		System.out.println("Second Reserve Status : " + secondReserveResponse.statusCode());

		System.out.println("Second Reserve Body   : " + secondReserveResponse.body());

		assertEquals(201, secondReserveResponse.statusCode());
	}

	@Test
	void shouldRejectSameIdempotencyKeyWithDifferentRequest() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2", "A3"));

		String idempotencyKey = UUID.randomUUID().toString();

		HttpResponse<String> firstResponse = reserveWithIdempotencyKey(showId, "idempotency-user", List.of("A1"),
				idempotencyKey);

		assertEquals(201, firstResponse.statusCode());

		HttpResponse<String> secondResponse = reserveWithIdempotencyKey(showId, "idempotency-user", List.of("A2"),
				idempotencyKey);

		System.out.println("First Status  : " + firstResponse.statusCode());

		System.out.println("Second Status : " + secondResponse.statusCode());

		System.out.println("Second Body   : " + secondResponse.body());

		assertEquals(409, secondResponse.statusCode());
	}

	@Test
	void shouldRejectReservationWithoutUserId() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2"));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/shows/" + showId + "/reserve"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
						{
						  "seats": ["A1"],
						  "idempotencyKey": "%s"
						}
						""".formatted(UUID.randomUUID()))).build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		System.out.println("Status : " + response.statusCode());

		System.out.println("Body   : " + response.body());

		assertEquals(401, response.statusCode());
	}

	@Test
	void shouldRejectInvalidReservationRequest() throws Exception {

		String showId = createShowWithSeats(List.of("A1", "A2"));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/shows/" + showId + "/reserve"))
				.header("Content-Type", "application/json").header("X-User-Id", "validation-test-user")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{
						  "seats": [],
						  "idempotencyKey": ""
						}
						""")).build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		System.out.println("Status : " + response.statusCode());
		System.out.println("Body   : " + response.body());

		assertEquals(400, response.statusCode());
	}

	private HttpResponse<String> reserveWithIdempotencyKey(String showId, String userId, List<String> seats,
			String idempotencyKey) throws Exception {

		String seatsJson = seats.stream().map(seat -> "\"" + seat + "\"")
				.collect(java.util.stream.Collectors.joining(","));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/shows/" + showId + "/reserve"))
				.header("Content-Type", "application/json").header("X-User-Id", userId)
				.POST(HttpRequest.BodyPublishers.ofString("""
						{
						  "seats": [%s],
						  "idempotencyKey": "%s"
						}
						""".formatted(seatsJson, idempotencyKey))).build();

		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String createShowWithSeats(List<String> seats) throws Exception {

		String showIdempotency = UUID.randomUUID().toString();

		String seatsJson = seats.stream().map(seat -> "\"" + seat + "\"")
				.collect(java.util.stream.Collectors.joining(","));

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/shows"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
						{
						  "name": "Automated Test %s",
						  "seats": [%s],
						  "pricePaise": 250000
						}
						""".formatted(showIdempotency, seatsJson))).build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(201, response.statusCode());

		String body = response.body();

		return body.split("\"id\":\"")[1].split("\"")[0];
	}

	private HttpResponse<String> reserve(String showId, String userId, List<String> seats) throws Exception {

		String seatsJson = seats.stream().map(seat -> "\"" + seat + "\"")
				.collect(java.util.stream.Collectors.joining(","));

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/shows/" + showId + "/reserve"))
				.header("Content-Type", "application/json").header("X-User-Id", userId)
				.POST(HttpRequest.BodyPublishers.ofString("""
						{
						  "seats": [%s],
						  "idempotencyKey": "%s"
						}
						""".formatted(seatsJson, UUID.randomUUID()))).build();

		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}