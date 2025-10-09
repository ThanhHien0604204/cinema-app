package com.ntth.spring_boot_heroku_cinema_app.service;

import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.util.MailConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final WebClient client;
    private final String fromEmail;
    private final String fromName;

    public EmailService(
            @Value("${spring.mail.username}") String apiKey,
            @Value("${app.mail.from}") String fromEmail,
            @Value("${app.mail.fromName:Movie App}") String fromName,
            WebClient.Builder builder
    ) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.client = builder
                .baseUrl("https://api.sendgrid.com/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void sendOtpEmail(String toEmail, String otp) {
        String html = """
            <p>Xin chào,</p>
            <p>Mã OTP đặt lại mật khẩu của bạn là: <b>%s</b></p>
            <p>OTP có hiệu lực trong 10 phút.</p>
        """.formatted(otp);

        // JSON đúng schema SendGrid
        String payload = """
        {
          "personalizations": [ { "to": [ { "email": "%s" } ] } ],
          "from": { "email": "%s", "name": "%s" },
          "subject": "Mã OTP đặt lại mật khẩu",
          "content": [ { "type": "text/html", "value": %s } ]
        }
        """.formatted(toEmail, fromEmail, fromName, toJsonString(html));

        client.post()
                .uri("/mail/send")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .map(body -> new RuntimeException("SendGrid API error " + resp.statusCode() + ": " + body)))
                .toBodilessEntity()
                .block(); // hoặc subscribe async theo nhu cầu
    }

    // escape chuỗi JSON đơn giản
    private static String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
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