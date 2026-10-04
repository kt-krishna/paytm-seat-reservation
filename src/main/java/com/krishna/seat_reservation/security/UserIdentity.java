package com.krishna.seat_reservation.security;

public class UserIdentity {

    private final String userId;

    public UserIdentity(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}