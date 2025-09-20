package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class ZaloPayController {
    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepo;

    @Value("${app.deeplink:}")
    private String deeplinkBase; // ví dụ: "myapp://zp-callback"

    @Value("${app.publicBaseUrl}")
    private String publicBaseUrl;

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    public ZaloPayController(TicketService bookingService) {
        this.ticketService = bookingService;
    }

    // 1) Tạo booking (ZaloPay) từ hold
    @PostMapping("/bookings/zalopay")
    public ResponseEntity<?> createBookingFromHold(@RequestBody Map<String,String> body,
                                                   @AuthenticationPrincipal JwtUser user) {
        String holdId = body.get("holdId");
        Ticket b = ticketService.createBookingZaloPay(holdId, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "bookingId", b.getId(),
                "bookingCode", b.getBookingCode(),
                "status", b.getStatus(),
                "amount", b.getAmount()
        ));
    }

    // 2) Tạo order URL để client mở
    @PostMapping("/payments/zalopay/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String,String> body,
                                         @AuthenticationPrincipal JwtUser user) {
        String bookingId = body.get("bookingId");
        String appUser = user.getUserId(); // hoặc email/phone
        return ResponseEntity.ok(ticketService.createZpOrderLink(bookingId, appUser));
    }

    // 3) IPN callback từ ZaloPay (public)
    @PostMapping(value = "/payments/zalopay/ipn", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> ipn(
            @RequestParam Map<String, String> form,
            @RequestBody(required = false) String body,
            @RequestHeader(value = "Content-Type", required = false) String contentType
    ) {
        try {
            // Gom hết tham số vào 1 map (dùng cho TicketService.handleZpIpn như cũ)
            Map<String, String> params = new HashMap<>();
            if (form != null) params.putAll(form);

            // 1) Nếu form rỗng mà có body, thử parse theo content-type
            if ((params.isEmpty() || !params.containsKey("data")) && body != null && !body.isBlank()) {
                if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                    var om = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String,Object> json = om.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    if (json.get("data") != null) params.put("data", String.valueOf(json.get("data")));
                    if (json.get("mac")  != null) params.put("mac",  String.valueOf(json.get("mac")));
                } else {
                    for (String p : body.split("&")) {
                        int i = p.indexOf('=');
                        if (i > 0) {
                            String k = java.net.URLDecoder.decode(p.substring(0,i), java.nio.charset.StandardCharsets.UTF_8);
                            String v = java.net.URLDecoder.decode(p.substring(i+1), java.nio.charset.StandardCharsets.UTF_8);
                            params.put(k, v);
                        }
                    }
                }
            }

            // 2) Lấy bookingId từ embed_data bên trong "data" nếu có, rồi NHÉT vào params
            try {
                String data = params.get("data");
                if (data != null) {
                    var om = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String,Object> dataJson = om.readValue(data, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    Object embedObj = dataJson.get("embed_data");
                    Map<String,Object> embed = null;
                    if (embedObj instanceof String) {
                        embed = om.readValue((String) embedObj, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    } else if (embedObj instanceof Map) {
                        embed = (Map<String, Object>) embedObj;
                    }
                    if (embed != null && embed.get("bookingId") != null && !params.containsKey("bookingId")) {
                        params.put("bookingId", String.valueOf(embed.get("bookingId")));
                    }
                }
            } catch (Exception ignore) {
                // Không chặn flow nếu parse embed_data lỗi — service có thể tự xử lý
            }

            // 3) Gọi service cũ, giữ nguyên logic bên trong của bạn
            ticketService.handleZpIpn(params);

            // 4) ZaloPay cần return_code=1 để biết bạn đã nhận thành công
            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "success"));
        } catch (Exception e) {
            // Vẫn trả 1 để ZP không spam retry (tuỳ chính sách của bạn)
            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "error"));
        }
    }


    //dùng để dựng URL quay về ứng dụng sau khi thanh toán xong
//    @GetMapping(value = "/payments/zalopay/return", produces = MediaType.TEXT_HTML_VALUE)
//    public ResponseEntity<String> zpReturn(@RequestParam(required=false) String bookingId,
//                                           @RequestParam(required=false) String canceled) {
//        if (deeplinkBase == null || deeplinkBase.isBlank()) {
//            return ResponseEntity.ok("<html><body>Thiếu app.deeplink. Vui lòng mở lại ứng dụng.</body></html>");
//        }
//        String sep = deeplinkBase.contains("?") ? "&" : "?";
//        String target = deeplinkBase + (bookingId!=null? sep+"bookingId="+bookingId : "");
//        if (canceled!=null) target += (target.contains("?")?"&":"?") + "canceled=1";
//        String html = "<!doctype html><meta http-equiv='refresh' content='0;url="+target+"'>" +
//                "<a href='"+target+"'>Mở ứng dụng</a>";
//        return ResponseEntity.ok(html);
//    }

    // 4) Hủy vé + refund (ADMIN hoặc chủ vé, tùy policy)
    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id, @RequestBody(required = false) Map<String,String> body) {
        String reason = (body == null) ? "" : body.getOrDefault("reason", "");
        Ticket b = ticketService.cancelBooking(id, reason);
        return ResponseEntity.ok(Map.of(
                "bookingId", b.getId(),
                "status", b.getStatus()
        ));
    }

    /**
     * 4.1) API cho app gọi ngay khi quay về từ ZaloPay để kiểm tra trạng thái
     * Thay vì poll /api/bookings/{id}, endpoint này xử lý nhanh hơn
     */
    @GetMapping("/payments/zalopay/status/{bookingId}")
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(
            @PathVariable String bookingId,
            @AuthenticationPrincipal JwtUser user) {

        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // Kiểm tra quyền sở hữu
        boolean isAdmin = user != null && "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && b.getUserId() != null && !Objects.equals(b.getUserId(), user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingId", b.getId());
        result.put("bookingCode", b.getBookingCode());

        // THAY ĐỔI: CẢ CONFIRMED VÀ PENDING_PAYMENT ĐỀU TRẢ "SUCCESS"
        String displayStatus = "PENDING_PAYMENT".equalsIgnoreCase(b.getStatus()) ||
                "CONFIRMED".equalsIgnoreCase(b.getStatus()) ?
                "SUCCESS" : b.getStatus().toUpperCase();

        result.put("status", displayStatus);

        // Nếu SUCCESS (CONFIRMED hoặc PENDING_PAYMENT), trả thêm info
        if ("SUCCESS".equalsIgnoreCase(displayStatus)) {
            result.put("seats", b.getSeats());
            result.put("showtimeId", b.getShowtimeId());
            try {
                result.put("amount", b.getAmount());
            } catch (Throwable ignore) {}

            // Thêm payment info
            if (b.getPayment() != null) {
                Map<String, Object> payment = new LinkedHashMap<>();
                payment.put("gateway", b.getPayment().getGateway());
                if (b.getPayment().getTxId() != null) {
                    payment.put("txId", b.getPayment().getTxId());
                }
                result.put("payment", payment);
            }
        }

        log.info("Status check - bookingId={}, internalStatus={}, displayStatus={}",
                bookingId, b.getStatus(), displayStatus);

        return ResponseEntity.ok(result);
    }

    // 5) Cập nhật zpReturn để gọi status check
    @GetMapping(value = "/payments/zalopay/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> zpReturn(@RequestParam(required=false) String bookingId,
                                           @RequestParam(required=false) String canceled) {
        if (deeplinkBase == null || deeplinkBase.isBlank()) {
            return ResponseEntity.ok("Thiếu app.deeplink. Vui lòng mở lại ứng dụng.");
        }

        String sep = deeplinkBase.contains("?") ? "&" : "?";
        String target = deeplinkBase + sep + "bookingId=" + bookingId;
        if (canceled != null) {
            target += (target.contains("?") ? "&" : "?") + "canceled=1";
        }

        // THÊM GỌI STATUS CHECK TRƯỚC KHI QUAY VỀ APP
        String statusCheckUrl = ensureNoTrailingSlash(publicBaseUrl) +
                "/api/payments/zalopay/status/" + bookingId;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Đang xử lý thanh toán...</title>
                <script>
                    // 1. Gọi API kiểm tra trạng thái ngay lập tức
                    async function checkStatus() {
                        try {
                            const response = await fetch('%s', {
                                method: 'GET',
                                headers: {
                                    'Authorization': localStorage.getItem('token') || sessionStorage.getItem('token'),
                                    'Content-Type': 'application/json'
                                }
                            });
                            
                            if (response.ok) {
                                const result = await response.json();
                                if (result.status === 'CONFIRMED') {
                                    // Thành công - redirect về app với thông tin
                                    window.location.href = '%s&status=CONFIRMED';
                                } else {
                                    // Lỗi - redirect với thông tin lỗi
                                    window.location.href = '%s&status=FAILED';
                                }
                            } else {
                                // Network error hoặc 4xx/5xx
                                window.location.href = '%s&status=ERROR';
                            }
                        } catch (error) {
                            console.error('Status check failed:', error);
                            window.location.href = '%s&status=ERROR';
                        }
                    }
                    
                    // Tự động check sau 500ms để đảm bảo IPN đã xử lý xong
                    setTimeout(checkStatus, 500);
                    
                    // Fallback sau 5s nếu không redirect được
                    setTimeout(() => {
                        window.location.href = '%s';
                    }, 5000);
                </script>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        display: flex; 
                        justify-content: center; 
                        align-items: center; 
                        height: 100vh; 
                        margin: 0; 
                        background: #f5f5f5;
                    }
                    .container {
                        text-align: center;
                        padding: 20px;
                        background: white;
                        border-radius: 10px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .spinner {
                        border: 4px solid #f3f3f3;
                        border-top: 4px solid #007bff;
                        border-radius: 50%;
                        width: 40px;
                        height: 40px;
                        animation: spin 1s linear infinite;
                        margin: 0 auto 20px;
                    }
                    @keyframes spin {
                        0% { transform: rotate(0deg); }
                        100% { transform: rotate(360deg); }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="spinner"></div>
                    <h3>Đang xử lý thanh toán...</h3>
                    <p>Vui lòng chờ trong giây lát</p>
                </div>
            </body>
            </html>
            """.formatted(
                statusCheckUrl,                    // %s 1: status check URL
                target,                           // %s 2: success redirect
                target,                           // %s 3: failed redirect
                target,                           // %s 4: error redirect
                target,                           // %s 5: network error redirect
                target                            // %s 6: fallback redirect
        );

        return ResponseEntity.ok().body(html);
    }

    // Helper method (thêm vào cuối class)
    private String ensureNoTrailingSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}