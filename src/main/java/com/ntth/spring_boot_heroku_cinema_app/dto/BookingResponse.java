package com.ntth.spring_boot_heroku_cinema_app.dto;


import com.ntth.spring_boot_heroku_cinema_app.pojo.BookingStatus;

import java.time.Instant;
import java.util.List;

public record BookingResponse(
        String id,
        String showtimeId,
        List<String> seats,
        Integer pricePerSeat,
        Integer totalPrice,
        BookingStatus status,
        Instant createdAt
) {}
