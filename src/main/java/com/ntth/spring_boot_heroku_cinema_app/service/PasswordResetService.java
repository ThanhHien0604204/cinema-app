package com.ntth.spring_boot_heroku_cinema_app.service;

import com.ntth.spring_boot_heroku_cinema_app.dto.ForgotPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.dto.ResetPasswordRequest;
import com.ntth.spring_boot_heroku_cinema_app.pojo.PasswordResetToken;
import com.ntth.spring_boot_heroku_cinema_app.pojo.User;
import com.ntth.spring_boot_heroku_cinema_app.repository.PasswordResetTokenRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class PasswordResetService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PasswordResetTokenRepository tokenRepo;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Bước 1: Yêu cầu reset → Tạo & gửi OTP
    public void requestReset(ForgotPasswordRequest req) {
        String email = req.email().trim().toLowerCase();

        if (!userRepo.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email không tồn tại");
        }

        // Xóa OTP cũ nếu có
        tokenRepo.findByEmailAndUsedFalse(email).ifPresent(tokenRepo::delete);

        // Tạo OTP 6 số
        String otp = generateOtp();
        LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(5);

        // Lưu vào DB
        PasswordResetToken resetToken = new PasswordResetToken(email, otp, expiryDate);
        tokenRepo.save(resetToken);

        // Gửi email chứa OTP
        emailService.sendOtpEmail(email, otp);
    }

    // Bước 2: Verify OTP & reset password
    public void resetPassword(ResetPasswordRequest req) {
        // Tìm OTP từ DB
        PasswordResetToken token = tokenRepo.findByOtpAndUsedFalse(req.otp())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP không hợp lệ hoặc đã sử dụng"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP đã hết hạn");
        }

        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp");
        }

        User user = userRepo.findByEmail(token.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Cập nhật password
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        // Đánh dấu OTP đã dùng
        token.setUsed(true);
        tokenRepo.save(token);
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otpNumber = 100000 + random.nextInt(900000);  // 100000 to 999999
        return String.valueOf(otpNumber);
    }
}