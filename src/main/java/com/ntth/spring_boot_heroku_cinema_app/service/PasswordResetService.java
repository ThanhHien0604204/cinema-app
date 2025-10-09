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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // TTL cấu hình trong properties (mặc định 10 phút)
    @Value("${app.reset.ttl-minutes:10}")
    private int otpTtlMinutes;

    public PasswordResetService(UserRepository userRepo,
                                PasswordResetTokenRepository tokenRepo,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Bước 1: Yêu cầu reset → Tạo & gửi OTP */
    @Transactional
    public void requestReset(ForgotPasswordRequest req) {
        String email = req.email().trim().toLowerCase();
        log.info("Request reset for email: {}", email);

        // (A) Nếu muốn chống 'user enumeration' hãy bỏ block này và luôn trả 200.
        if (!userRepo.existsByEmail(email)) {
            log.warn("Email not found: {}", email);
            throw new IllegalArgumentException("Email không tồn tại");
        }

        // Xoá OTP chưa dùng trước đó (nếu có)
        tokenRepo.findByEmailAndUsedFalse(email).ifPresent(old -> {
            log.info("Deleting old token for email: {}", email);
            tokenRepo.delete(old);
        });

        // Tạo OTP 6 số + TTL
        String otp = generateOtp6();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(otpTtlMinutes);

        PasswordResetToken token = new PasswordResetToken(email, otp, expiry);
        tokenRepo.save(token);
        log.info("Saved OTP for email: {}, expires at: {}", email, expiry);

        // Gửi email – nếu fail, rollback token cho sạch
        try {
            emailService.sendOtpEmail(email, otp); // dùng JavaMailSender bên dưới
            log.info("OTP email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            // xoá token vừa tạo để tránh rác
            tokenRepo.delete(token);
            throw new RuntimeException("Lỗi gửi email. Vui lòng thử lại sau.");
        }
    }

    /** Bước 2: Verify OTP & reset password */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken token = tokenRepo.findByOtpAndUsedFalse(req.otp())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP không hợp lệ hoặc đã sử dụng"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP đã hết hạn");
        }

        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp");
        }

        // (tuỳ chọn) Ràng buộc độ mạnh mật khẩu
        if (req.newPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu tối thiểu 6 ký tự");
        }

        User user = userRepo.findByEmail(token.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        // Đánh dấu token đã dùng + vô hiệu hoá các token khác còn mở cho email này (nếu muốn)
        token.setUsed(true);
        tokenRepo.save(token);
        tokenRepo.deleteAllByEmailAndUsedFalse(token.getEmail());
    }

    private String generateOtp6() {
        int n = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return String.valueOf(n);
    }
}

//    private String generateOtp() {
//        SecureRandom random = new SecureRandom();
//        int otpNumber = 100000 + random.nextInt(900000);  // 100000 to 999999
//        return String.valueOf(otpNumber);
//    }