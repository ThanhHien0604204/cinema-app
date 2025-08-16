package com.ntth.spring_boot_heroku_cinema_app.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ShowtimeResponse(
        String sessionId,   // map từ entity.id
        String movieId,
        String roomId,
        String sessionName,
        String startAt, // "18:00"
        String endAt,
        Integer price,
        Integer totalSeats,
        Integer availableSeats,
        LocalDate date
) {}



