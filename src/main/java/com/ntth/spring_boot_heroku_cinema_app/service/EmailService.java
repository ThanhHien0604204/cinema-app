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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class EmailService {

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public EmailService(
            @Value("${app.sendgrid.api-key:}") String apiKey,
            @Value("${app.mail.from:}") String fromEmail,
            @Value("${app.mail.fromName:Movie App}") String fromName
    ) {
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : null;
        this.fromEmail = fromEmail;
        this.fromName = fromName;

        if (this.apiKey == null) {
            LoggerFactory.getLogger(getClass())
                    .warn("SENDGRID API KEY is missing -> EmailService disabled (forgot-password sẽ không gửi mail).");
        }
    }

    public void sendOtpEmail(String toEmail, String otp) {
        if (apiKey == null) {
            throw new IllegalStateException("Email is disabled: missing SENDGRID_API_KEY");
        }
        String html = """
            <p>Xin chào,</p>
            <p>Mã OTP đặt lại mật khẩu của bạn là: <b>%s</b></p>
            <p>OTP có hiệu lực trong 10 phút.</p>
        """.formatted(otp);

        // JSON đúng schema SendGrid
        String json = """
        {
          "personalizations": [ { "to": [ { "email": "%s" } ] } ],
          "from": { "email": "%s", "name": "%s" },
          "subject": "Mã OTP đặt lại mật khẩu",
          "content": [ { "type": "text/html", "value": %s } ]
        }
        """.formatted(toEmail, fromEmail, fromName, toJson(html));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            String msgId = res.headers().firstValue("X-Message-Id").orElse("-");
            log.info("SendGrid response: code={}, X-Message-Id={}", code, msgId);
            if (code >= 400) {
                log.error("SendGrid error body: {}", res.body());
                throw new RuntimeException("SendGrid error " + code + ": " + res.body());
            }
        } catch (Exception e) {
            // quăng để service upper layer xử (hoặc bạn có thể chỉ log và trả 200 tuỳ chính sách)
            throw new RuntimeException("Gửi email thất bại: " + e.getMessage(), e);
        }
    }

    private static String toJson(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
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