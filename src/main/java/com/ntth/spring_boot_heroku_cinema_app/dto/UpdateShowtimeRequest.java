package com.ntth.spring_boot_heroku_cinema_app.dto;

public record UpdateShowtimeRequest(
        String movieId,
        String roomId,
        String sessionName,
        String date, // "yyyy-MM-dd"
        String startTime, // "HH:mm"
        String endTime, // "HH:mm"
        Integer price,
        Integer totalSeats,
        Integer availableSeats
) {}
