package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record HoldSeatsRequest(
        @NotBlank String showtimeId,
        @NotEmpty List<String> seats, // ["A1","A2"]
        Integer holdSeconds // optional, default 300
) {}