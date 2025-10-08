package com.ntth.spring_boot_heroku_cinema_app.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.util.MailConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @PostConstruct
    public void init() {
        // Kiểm tra env vars
        String username = System.getenv("SPRING_MAIL_USERNAME");
        String password = System.getenv("SPRING_MAIL_PASSWORD");
        logger.info("Env Vars - Username: {}, Password: {}", username, password);

        // Kiểm tra session properties từ mailSender
        Session session = mailSender.createMimeMessage().getSession();
        logger.info("Mail Session Properties: {}", session.getProperties());
    }

    @Retryable(
            value = {MessagingException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public void sendOtpEmail(String toEmail, String otp) {
        logger.info("Attempting to send OTP to: {} (Attempt)", toEmail);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Test OTP Email");
        message.setText("This is a test OTP: " + otp + " to verify mail config.");

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            mimeMessage.setFrom("no-reply@movie-ticket-app.com");
            mimeMessage.setRecipients(MimeMessage.RecipientType.TO, toEmail);
            mimeMessage.setSubject("Test OTP Email");
            mimeMessage.setText("This is a test OTP: " + otp + " to verify mail config.");

            Transport.send(mimeMessage);
            logger.info("Test OTP sent successfully to: {}", toEmail);
        } catch (SendFailedException e) {
            logger.error("Send failed for {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi email: " + e.getMessage());
        } catch (MessagingException e) {
            logger.error("Messaging error for {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Lỗi kết nối mail server: " + e.getMessage());
        }
    }
}
//    /**
//     * Gửi email reset password với link xác nhận (HTML)
//     */
//    public void sendPasswordResetEmail(String toEmail, String userName, String resetLink) {
//        try {
//            SimpleMailMessage message = new SimpleMailMessage();
//            message.setFrom(fromEmail);
//            message.setTo(toEmail);
//            message.setSubject("🔒 Đặt lại mật khẩu - Movie Ticket Booking");
//            message.setText(buildPasswordResetEmailBody(userName, resetLink));  // Text version cho test
//
//            mailSender.send(message);
//            log.info("✅ Email reset password đã gửi thành công đến: {}", toEmail);
//
//        } catch (MailException e) {
//            log.error("❌ Lỗi gửi email reset password đến {}: {}", toEmail, e.getMessage(), e);
//            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.", e);
//        }
//    }
//    /**
//     * Gửi email thông báo đơn giản (text) - dùng cho các email khác
//     */
//    public void send(String to, String subject, String text) {
//        try {
//            SimpleMailMessage message = new SimpleMailMessage();
//            message.setFrom(fromEmail);
//            message.setTo(to);
//            message.setSubject(subject);
//            message.setText(text);
//
//            mailSender.send(message);
//            log.info("✅ Email đã gửi thành công đến: {}", to);
//
//        } catch (MailException e) {
//            log.error("❌ Lỗi gửi email đến {}: {}", to, e.getMessage(), e);
//            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.", e);
//        }
//    }