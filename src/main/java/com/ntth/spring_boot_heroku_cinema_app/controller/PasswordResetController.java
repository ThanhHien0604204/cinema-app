package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.service.PasswordResetService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    // B1: Yêu cầu đổi mật khẩu (PUBLIC)
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotPasswordRequest req,
                                    @RequestHeader(value = "X-App-Base-Url", required = false) String baseUrl) {
        log.info("POST /api/forgot-password - Email: {}", req.email());

        try {
            // baseUrl để build link; nếu không truyền, dùng domain deploy
            String appBaseUrl = (baseUrl != null && !baseUrl.trim().isBlank())
                    ? baseUrl.trim()
                    : "https://movie-ticket-booking-app-fvau.onrender.com";

            service.requestReset(req, appBaseUrl);

            // Luôn trả 200 để tránh lộ thông tin email tồn tại hay không
            return ResponseEntity.ok(Map.of(
                    "message", "Nếu email tồn tại, bạn sẽ nhận được link đặt lại mật khẩu trong vài phút",
                    "success", true
            ));

        } catch (Exception e) {
            log.error("Lỗi xử lý forgot password: {}", e.getMessage(), e);
            // Vẫn trả 200 để tránh lộ thông tin
            return ResponseEntity.ok(Map.of(
                    "message", "Có lỗi xảy ra. Vui lòng thử lại sau.",
                    "success", false
            ));
        }
    }

    // B2: Xác nhận token & đặt mật khẩu mới (PUBLIC)
    @PostMapping("/reset-password")
    public ResponseEntity<?> reset(@Valid @RequestBody ResetPasswordRequest req) {
        log.info("POST /api/reset-password - Token: {} (length: {})",
                req.token().substring(0, Math.min(10, req.token().length())) + "...", req.token().length());

        try {
            service.resetPassword(req);

            return ResponseEntity.ok(Map.of(
                    "message", "Mật khẩu đã được đặt lại thành công. Bạn có thể đăng nhập ngay bây giờ.",
                    "success", true
            ));

        } catch (ResponseStatusException e) {
            log.warn("Lỗi reset password: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason(), "success", false));

        } catch (Exception e) {
            log.error("Lỗi không mong muốn khi reset password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Có lỗi xảy ra. Vui lòng thử lại sau.", "success", false));
        }
    }
}
