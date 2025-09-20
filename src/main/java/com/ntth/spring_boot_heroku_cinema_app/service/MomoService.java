//package com.ntth.spring_boot_heroku_cinema_app.service;
//
//import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
//import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
//import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.nio.charset.StandardCharsets;
//import java.time.Instant;
//import java.util.*;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//
//@Service
//public class MomoService {
//    private static final Logger log = LoggerFactory.getLogger(MomoService.class);
//
//    private final TicketRepository ticketRepo;
//
//    // Test creds (ví dụ). Hãy thay bằng thông tin của bạn trong cấu hình.
//    private final String partnerCode = "MOMO";
//    private final String accessKey   = "F8BBA842ECF85";
//    private final String secretKey   = "K951B6PE1waDMi640xX08PD3vg6EkVlz";
//    private final String endpoint    = "https://test-payment.momo.vn/v2/gateway/api/create";
//
//    public MomoService(TicketRepository ticketRepo) {
//        this.ticketRepo = ticketRepo;
//    }
//
//    public Map<String, Object> createOrder(String bookingId, JwtUser user) {
//        Ticket b = ticketRepo.findById(bookingId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
//
//        // Optional: kiểm tra quyền sở hữu
//        if (user != null && b.getUserId() != null && !Objects.equals(user.getUserId(), b.getUserId())) {
//            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
//        }
//
//        long amount = (b.getAmount() == null) ? 0L : b.getAmount();
//        if (amount <= 0) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT");
//        }
//
//        // orderId = bookingCode để IPN lookup nhanh
//        String orderId = b.getBookingCode();
//        String requestId = UUID.randomUUID().toString();
//        String orderInfo = "MoMo pay booking " + orderId;
//        String redirectUrl = ""; // bạn có thể để deep link app nếu cần
//        String ipnUrl = "https://your-domain.com/api/payments/momo/ipn"; // đổi sang domain của bạn
//
//        // extraData nên nhét bookingId để đối chiếu
//        String extraData = "bookingId=" + b.getId();
//
//        // rawSignature theo QuickPay mẫu bạn đưa
//        String rawSig = "accessKey=" + accessKey +
//                "&amount=" + amount +
//                "&extraData=" + extraData +
//                "&orderId=" + orderId +
//                "&orderInfo=" + orderInfo +
//                "&partnerCode=" + partnerCode +
//                "&paymentCode=" + "" +
//                "&requestId=" + requestId;
//
//        String signature = hmacSHA256(rawSig, secretKey);
//
//        Map<String, Object> req = new LinkedHashMap<>();
//        req.put("partnerCode", partnerCode);
//        req.put("accessKey", accessKey);
//        req.put("requestId", requestId);
//        req.put("amount", amount);
//        req.put("orderId", orderId);
//        req.put("orderInfo", orderInfo);
//        req.put("redirectUrl", redirectUrl);
//        req.put("ipnUrl", ipnUrl);
//        req.put("extraData", extraData);
//        req.put("requestType", "captureWallet");
//        req.put("autoCapture", true);
//        req.put("lang", "vi");
//        req.put("signature", signature);
//
//        // Gọi MoMo tạo đơn (test endpoint)
//        RestTemplate rt = new RestTemplate();
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(req, headers);
//
//        ResponseEntity<Map> resp = rt.postForEntity(endpoint, httpEntity, Map.class);
//        Map<String, Object> body = resp.getBody() == null ? new LinkedHashMap<>() : resp.getBody();
//
//        // Trả về cho FE dùng: payUrl/deeplink (tùy response MoMo)
//        Map<String, Object> out = new LinkedHashMap<>();
//        out.put("payUrl", body.getOrDefault("payUrl", body.get("deeplink")));
//        out.put("deeplink", body.get("deeplink"));
//        out.put("orderId", orderId);
//        out.put("requestId", requestId);
//        out.put("amount", amount);
//        return out;
//    }
//
//    private static String hmacSHA256(String data, String key) {
//        try {
//            Mac mac = Mac.getInstance("HmacSHA256");
//            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
//            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
//            StringBuilder sb = new StringBuilder();
//            for (byte b : result) sb.append(String.format("%02x", b));
//            return sb.toString();
//        } catch (Exception e) {
//            throw new IllegalStateException("HMAC error", e);
//        }
//    }
//}
