package com.ntth.spring_boot_heroku_cinema_app.repository;

import com.ntth.spring_boot_heroku_cinema_app.pojo.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    // Tìm token còn hiệu lực (chưa dùng) theo email
    Optional<PasswordResetToken> findByEmailAndUsedFalse(String email);

    // Tìm token chưa dùng theo OTP (để reset)
    Optional<PasswordResetToken> findByOtpAndUsedFalse(String otp);

    // Xoá tất cả token CHƯA DÙNG của 1 email (dọn sạch sau khi reset thành công)
    void deleteAllByEmailAndUsedFalse(String email);

    // (tuỳ chọn) Xoá toàn bộ token quá hạn
    @Query("{ 'expiryDate': { $lt: ?0 } }")
    void deleteByExpiryDateBefore(LocalDateTime now);
}
