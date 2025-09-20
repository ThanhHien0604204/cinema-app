package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class MomoService {

    @Value("${momo.partnerCode:MOMO}")
    private String partnerCode;

    @Value("${momo.accessKey:F8BBA842ECF85}")
    private String accessKey;

    @Value("${momo.secretKey:K951B6PE1waDMi640xX08PD3vg6EkVlz}")
    private String secretKey;

    // URL sandbox MoMo
    @Value("${momo.createUrl:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String createUrl;

    // Phải là URL public của app bạn (Render)
    @Value("${momo.ipnUrl:https://movie-ticket-booking-app-fvau.onrender.com/api/payments/momo/ipn}")
    private String ipnUrl;

    @Value("${momo.returnUrl:https://movie-ticket-booking-app-fvau.onrender.com/api/payments/momo/return}")
    private String returnUrl;

    private final com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository ticketRepo;
    private final ObjectMapper om = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    public MomoService(TicketRepository ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    public Map<String, Object> createOrder(String bookingId, String userId) {
        Ticket t = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("BOOKING_NOT_FOUND"));

        Assert.isTrue(Objects.equals(t.getUserId(), userId), "FORBIDDEN");
        Assert.isTrue("PENDING_PAYMENT".equals(t.getStatus()), "BOOKING_NOT_PENDING");
        Assert.notNull(t.getAmount(), "AMOUNT_REQUIRED");

        // orderId nên gắn bookingCode để đối soát
        String orderId = t.getBookingCode();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String orderInfo = "Thanh toan ve xem phim " + t.getBookingCode();
        String requestType = "captureWallet";

        // extraData dùng để map ngược khi IPN
        Map<String, String> extra = new HashMap<>();
        extra.put("bookingId", t.getId());
        extra.put("bookingCode", t.getBookingCode());
        String extraData;
        try {
            extraData = om.writeValueAsString(extra);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String raw =
                "accessKey="+accessKey+
                        "&amount="+t.getAmount()+
                        "&extraData="+extraData+
                        "&ipnUrl="+ipnUrl+
                        "&orderId="+orderId+
                        "&orderInfo="+orderInfo+
                        "&partnerCode="+partnerCode+
                        "&redirectUrl="+returnUrl+
                        "&requestId="+requestId+
                        "&requestType="+requestType;

        String signature = hmacSHA256(raw, secretKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", partnerCode);
        payload.put("accessKey", accessKey);
        payload.put("requestId", requestId);
        payload.put("amount", String.valueOf(t.getAmount()));
        payload.put("orderId", orderId);
        payload.put("orderInfo", orderInfo);
        payload.put("redirectUrl", returnUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("extraData", extraData);
        payload.put("requestType", requestType);
        payload.put("signature", signature);
        payload.put("lang", "vi");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> res = http.exchange(
                createUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
        );

        if (res.getBody() == null) throw new RuntimeException("MoMo empty response");
        Map<String,Object> body = res.getBody();
        int resultCode = Integer.parseInt(String.valueOf(body.getOrDefault("resultCode", 99)));
        if (resultCode != 0) {
            throw new RuntimeException("MoMo create failed: " + body);
        }

        // Trả payUrl & deeplink để FE mở
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("payUrl", body.get("payUrl"));
        out.put("deeplink", body.get("deeplink"));
        out.put("qrCodeUrl", body.get("qrCodeUrl"));
        out.put("orderId", orderId);
        out.put("requestId", requestId);
        return out;
    }

    public static String hmacSHA256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
