package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.*;

public record ReviewRequest(
        @NotBlank String movieId,
        @DecimalMin("1.0") @DecimalMax("5.0") double rating,
        @Size(max=2000) String content
) {}
