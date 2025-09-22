package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.PasswordResetToken;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.PasswordResetTokenRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepo,
                                PasswordResetTokenRepository tokenRepo,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    // B1: yêu cầu đổi mật khẩu
    public void requestReset(ForgotPasswordRequest req, String appBaseUrl) {
        String email = req.email().trim().toLowerCase();
        var userOpt = userRepo.findByEmail(email);

        // Luôn trả 200 để tránh lộ thông tin
        if (userOpt.isEmpty()) {
            log.debug("Email không tồn tại cho reset: {}", email);
            return;
        }

        User user = userOpt.get();
        log.info("Bắt đầu reset password cho user: {}", user.getEmail());

        // Tạo token
        String token = generateToken();
        Date expiresAt = Date.from(Instant.now().plus(15, ChronoUnit.MINUTES));
        PasswordResetToken prt = new PasswordResetToken(token, user.getId().toString(), expiresAt);
        tokenRepo.save(prt);

        // Tạo link
        String resetLink = appBaseUrl + "/api/reset-password?token=" + token;  // API endpoint

        // Gửi email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUserName(), resetLink);
            log.info("✅ Token reset {} gửi thành công cho {}", token.substring(0, 8) + "...", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Lỗi gửi email reset cho {}: {}", user.getEmail(), e.getMessage(), e);
            // Không throw để tránh lộ thông tin
        }
    }

    // B2: xác nhận token & đổi mật khẩu (giữ nguyên)
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu không khớp");
        }

        var tokenOpt = tokenRepo.findByTokenAndUsedFalse(req.token());
        var token = tokenOpt.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ hoặc đã sử dụng"));

        if (token.getExpiresAt().before(new Date())) {
            tokenRepo.delete(token); // Xóa token hết hạn
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token đã hết hạn");
        }

        User user = userRepo.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng không tồn tại"));

        // Kiểm tra mật khẩu mới không trùng mật khẩu cũ
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng mật khẩu cũ");
        }

        // Cập nhật mật khẩu
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);
        log.info("✅ Đã reset password thành công cho user: {}", user.getEmail());

        // Đánh dấu token đã dùng
        token.setUsed(true);
        tokenRepo.save(token);
        log.debug("Đã đánh dấu token {} là used", token.getToken());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        try {
            SecureRandom.getInstanceStrong().nextBytes(bytes);
        } catch (Exception e) {
            new SecureRandom().nextBytes(bytes);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}