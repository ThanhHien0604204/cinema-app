package com.ntth.spring_boot_heroku_cinema_app.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.*;

public record MovieRequest(
        @NotBlank String title,
        @NotBlank String imageUrl,
        @Positive Integer durationMinutes,
        @NotEmpty List<String> genre, // Danh sách tên thể loại
        @NotNull LocalDate movieDateStart,
        @DecimalMin("0.0") @DecimalMax("10.0") Double rating,
        @NotBlank String summary,
        String trailerUrl,
        String author,
        List<String> actors,
        Long views
) {

}
