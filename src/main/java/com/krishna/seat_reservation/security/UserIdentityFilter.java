package com.krishna.seat_reservation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class UserIdentityFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        String userId = request.getHeader(USER_ID_HEADER);

        try {
            MDC.put(REQUEST_ID_MDC_KEY, requestId);

            response.setHeader(REQUEST_ID_HEADER, requestId);

            if (userId != null && !userId.isBlank()) {
                request.setAttribute(USER_ID_ATTRIBUTE, userId);
            }

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}