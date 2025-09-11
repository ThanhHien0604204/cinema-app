package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.PasswordResetToken;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.PasswordResetTokenRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
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
        String email = req.email.trim().toLowerCase();
        var userOpt = userRepo.findByEmail(email);

        // Luôn trả 200 để tránh lộ thông tin tài khoản tồn tại hay không
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();

        // Tạo token ngẫu nhiên an toàn
        String token = generateToken();
        Date expiresAt = Date.from(Instant.now().plus(15, ChronoUnit.MINUTES));

        PasswordResetToken prt = new PasswordResetToken(token, user.getId(), expiresAt);
        tokenRepo.save(prt);

        String link = appBaseUrl + "/reset-password?token=" + token;
        String body = "Xin chào " + user.getUserName()
                + "\n\nBạn vừa yêu cầu đặt lại mật khẩu. Nhấn vào liên kết sau (có hiệu lực 15 phút):\n"
                + link + "\n\nNếu bạn không yêu cầu, vui lòng bỏ qua email này.";
        emailService.send(user.getEmail(), "Đặt lại mật khẩu", body);
    }

    // B2: xác nhận token & đổi mật khẩu
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.newPassword.equals(req.confirmNewPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu không khớp");
        }

        var token = tokenRepo.findByTokenAndUsedFalse(req.token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ"));

        if (token.getExpiresAt().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token đã hết hạn");
        }

        User user = userRepo.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng không tồn tại"));

        user.setPassword(passwordEncoder.encode(req.newPassword));
        userRepo.save(user);

        token.setUsed(true);
        tokenRepo.save(token);
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