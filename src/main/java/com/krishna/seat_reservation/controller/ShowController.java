package com.krishna.seat_reservation.controller;

import com.krishna.seat_reservation.dto.CreateShowRequest;
import com.krishna.seat_reservation.dto.ShowResponse;
import com.krishna.seat_reservation.service.ShowService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @Valid @RequestBody CreateShowRequest request) {

        ShowResponse response =
                showService.createShow(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{showId}")
    public ResponseEntity<ShowResponse> getShow(
            @PathVariable UUID showId) {

        return ResponseEntity.ok(
                showService.getShow(showId)
        );
    }
}