package com.ntth.spring_boot_heroku_cinema_app.pojo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@Document(collection = "password_reset_token")
public class PasswordResetToken {
    @Id
    private String id;
    private String email;         // Email user
    private String otp;           // OTP 6 số
    private LocalDateTime expiryDate; // Thời gian hết hạn
    private boolean used;         // Đã sử dụng chưa

    public PasswordResetToken(String email, String otp, LocalDateTime expiryDate) {
        this.email = email;
        this.otp = otp;
        this.expiryDate = expiryDate;
        this.used = false;
    }

    // Getters/Setters
    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getOtp() { return otp; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
