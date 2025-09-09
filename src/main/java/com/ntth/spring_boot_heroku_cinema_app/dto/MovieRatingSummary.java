package com.ntth.spring_boot_heroku_cinema_app.dto;

public record MovieRatingSummary(
        String movieId,
        double avgRating,
        int reviewCount) {}
