package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank String movieId,
        @Min(1) @Max(5) int rating,
        @Size(max=2000) String content
) {}
