package com.ntth.spring_boot_heroku_cinema_app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmBookingRequest(
        @NotBlank String showtimeId,
        @NotEmpty List<String> seats,
        String paymentMethod // "VNPay"/"ZaloPay"/"Cash" (mock)
) {}
