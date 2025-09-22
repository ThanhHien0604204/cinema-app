package com.ntth.spring_boot_heroku_cinema_app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Gửi email reset password với link xác nhận (HTML)
     */
    public void sendPasswordResetEmail(String toEmail, String userName, String resetLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("🔒 Đặt lại mật khẩu - Movie Ticket Booking");
            message.setText(buildPasswordResetEmailBody(userName, resetLink));  // Text version cho test

            mailSender.send(message);
            log.info("✅ Email reset password đã gửi thành công đến: {}", toEmail);

        } catch (MailException e) {
            log.error("❌ Lỗi gửi email reset password đến {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.", e);
        }
    }

    /**
     * Xây dựng nội dung email (text version - đơn giản cho test)
     * Sau này có thể dùng Thymeleaf cho HTML nếu cần
     */
    private String buildPasswordResetEmailBody(String userName, String resetLink) {
        return """
            Xin chào %s,
            
            Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Movie Ticket Booking.
            
            Nhấn vào liên kết sau để đặt lại mật khẩu (có hiệu lực 15 phút):
            %s
            
            Nếu bạn không yêu cầu, vui lòng bỏ qua email này.
            
            Trân trọng,
            Movie Ticket Booking Team
            """.formatted(userName, resetLink);
    }

    /**
     * Gửi email thông báo đơn giản (text) - dùng cho các email khác
     */
    public void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("✅ Email đã gửi thành công đến: {}", to);

        } catch (MailException e) {
            log.error("❌ Lỗi gửi email đến {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.", e);
        }
    }
}