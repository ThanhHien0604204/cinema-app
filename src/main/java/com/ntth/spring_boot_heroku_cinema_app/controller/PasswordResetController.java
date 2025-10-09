package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.service.PasswordResetService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PasswordResetController {

    @Autowired
    private PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    // Endpoint yêu cầu reset (gửi OTP)
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        service.requestReset(request);
        return ResponseEntity.ok("OTP đã được gửi đến email của bạn nếu tồn tại. Kiểm tra hòm thư.");
    }

    // Endpoint verify OTP & reset password
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        service.resetPassword(request);
        return ResponseEntity.ok("Mật khẩu đã được đặt lại thành công.");
    }
}