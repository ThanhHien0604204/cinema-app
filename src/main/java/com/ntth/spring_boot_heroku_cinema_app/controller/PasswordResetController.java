package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    // B1: yêu cầu đổi mật khẩu (PUBLIC)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req,
                                       @RequestHeader(value = "X-App-Base-Url", required = false) String baseUrl) {
        // baseUrl để build link; nếu không truyền, dùng domain deploy của bạn
        String appBaseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.trim()
                : "https://movie-ticket-booking-app-fvau.onrender.com";
        service.requestReset(req, appBaseUrl);
        return ResponseEntity.ok().build(); // luôn 200
    }

    // B2: xác nhận token & đặt mật khẩu mới (PUBLIC)
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        service.resetPassword(req);
        return ResponseEntity.noContent().build(); // 204
    }
}
