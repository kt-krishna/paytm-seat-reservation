package com.krishna.seat_reservation.dto;

import java.util.List;
import java.util.UUID;

public class ShowResponse {

    private UUID id;
    private String name;
    private Long pricePaise;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer heldSeats;
    private Integer confirmedSeats;
    private List<ShowSeatResponse> seats;

    public ShowResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(Integer totalSeats) {
        this.totalSeats = totalSeats;
    }

    public Integer getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(Integer availableSeats) {
        this.availableSeats = availableSeats;
    }

    public Integer getHeldSeats() {
        return heldSeats;
    }

    public void setHeldSeats(Integer heldSeats) {
        this.heldSeats = heldSeats;
    }

    public Integer getConfirmedSeats() {
        return confirmedSeats;
    }

    public void setConfirmedSeats(Integer confirmedSeats) {
        this.confirmedSeats = confirmedSeats;
    }

    public List<ShowSeatResponse> getSeats() {
        return seats;
    }

    public void setSeats(List<ShowSeatResponse> seats) {
        this.seats = seats;
    }
}