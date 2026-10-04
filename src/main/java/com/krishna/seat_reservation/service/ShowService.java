package com.krishna.seat_reservation.service;

import com.krishna.seat_reservation.dto.CreateShowRequest;
import com.krishna.seat_reservation.dto.ShowResponse;
import com.krishna.seat_reservation.dto.ShowSeatResponse;
import com.krishna.seat_reservation.entity.Show;
import com.krishna.seat_reservation.entity.ShowSeat;
import com.krishna.seat_reservation.exception.ResourceNotFoundException;
import com.krishna.seat_reservation.repository.ShowRepository;
import com.krishna.seat_reservation.repository.ShowSeatRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowService(
            ShowRepository showRepository,
            ShowSeatRepository showSeatRepository) {

        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {

        List<String> uniqueSeats = request.getSeats()
                .stream()
                .map(String::trim)
                .filter(seat -> !seat.isBlank())
                .distinct()
                .sorted()
                .toList();

        if (uniqueSeats.isEmpty()) {
            throw new IllegalArgumentException("At least one seat is required");
        }

        if (uniqueSeats.size() != request.getSeats().size()) {
            throw new IllegalArgumentException("Duplicate seat numbers are not allowed");
        }

        Show show = new Show();

        UUID showId = UUID.randomUUID();

        show.setId(showId);
        show.setName(request.getName().trim());
        show.setPricePaise(request.getPricePaise());
        show.setMaxSeatsPerUser(4);
        show.setCreatedAt(OffsetDateTime.now());

        showRepository.save(show);

        List<ShowSeat> seats = new ArrayList<>();

        for (String seatNumber : uniqueSeats) {

            ShowSeat seat = new ShowSeat();

            seat.setShowId(showId);
            seat.setSeatNumber(seatNumber);
            seat.setStatus(ShowSeat.SeatStatus.AVAILABLE);

            seats.add(seat);
        }

        showSeatRepository.saveAll(seats);

        return buildShowResponse(show, seats);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Show not found: " + showId));

        List<ShowSeat> seats =
                showSeatRepository.findByShowIdOrderBySeatNumber(showId);

        return buildShowResponse(show, seats);
    }

    private ShowResponse buildShowResponse(
            Show show,
            List<ShowSeat> seats) {

        int available = 0;
        int held = 0;
        int confirmed = 0;

        List<ShowSeatResponse> seatResponses = new ArrayList<>();

        for (ShowSeat seat : seats) {

            switch (seat.getStatus()) {

                case AVAILABLE -> available++;

                case HELD -> held++;

                case CONFIRMED -> confirmed++;
            }

            seatResponses.add(
                    new ShowSeatResponse(
                            seat.getSeatNumber(),
                            seat.getStatus().name()
                    )
            );
        }

        ShowResponse response = new ShowResponse();

        response.setId(show.getId());
        response.setName(show.getName());
        response.setPricePaise(show.getPricePaise());

        response.setTotalSeats(seats.size());
        response.setAvailableSeats(available);
        response.setHeldSeats(held);
        response.setConfirmedSeats(confirmed);

        response.setSeats(seatResponses);

        return response;
    }
}