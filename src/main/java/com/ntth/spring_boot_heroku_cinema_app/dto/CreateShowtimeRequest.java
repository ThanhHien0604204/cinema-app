package com.ntth.spring_boot_heroku_cinema_app.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CreateShowtimeRequest(
        String movieId,
        String roomId,
        String sessionName,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        @Pattern(regexp="^\\d{2}:\\d{2}$", message = "startTime must be in HH:mm format")
        String startTime,  // "14:00"
        @Pattern(regexp="^\\d{2}:\\d{2}$", message = "endTime must be in HH:mm format")
        String endTime,  // "17:45"
        Integer price,
        Integer totalSeats,
        Integer availableSeats
) {}
