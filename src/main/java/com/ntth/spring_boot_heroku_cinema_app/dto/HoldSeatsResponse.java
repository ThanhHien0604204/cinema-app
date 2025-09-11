package com.ntth.spring_boot_heroku_cinema_app.dto;


import java.time.Instant;
import java.util.List;

public record HoldSeatsResponse(
        String holdId,             // dùng UUID, thực tế là token quy chiếu group hold
        String showtimeId,
        List<String> seats,
        Instant expiresAt
) {}