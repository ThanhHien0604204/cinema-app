package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import com.ntth.spring_boot_heroku_cinema_app.service.ZaloPayService;
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

    @Autowired
    private ZaloPayService zaloPayService;

    @Value("${app.deeplink:}")
    private String deeplinkBase; // ví dụ: "myapp://zp-callback"

    @Value("${app.publicBaseUrl}")
    private String publicBaseUrl;

    private static final Logger log = LoggerFactory.getLogger(ZaloPayController.class);

    public ZaloPayController(TicketService bookingService) {
        this.ticketService = bookingService;
    }

    // 1) Tạo booking (ZaloPay) từ hold
    @PostMapping("/bookings/zalopay")
    public ResponseEntity<?> createBookingFromHold(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal JwtUser user) {
        try {
            String holdId = (String) body.get("holdId");
            if (holdId == null || holdId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "MISSING_HOLD_ID"));
            }

            Ticket b = ticketService.createBookingZaloPay(holdId, user.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "bookingId", b.getId(),
                    "bookingCode", b.getBookingCode(),
                    "status", b.getStatus(),
                    "amount", b.getAmount()
            ));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Create ZaloPay booking failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", ex.getMessage()));
        }
    }

    // 2) Tạo order URL để client mở
    @PostMapping("/payments/zalopay/create")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal JwtUser user) {
        try {
            String bookingId = (String) body.get("bookingId");
            if (bookingId == null || bookingId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "MISSING_BOOKING_ID"));
            }

            Ticket booking = ticketRepo.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

            // SỬA: Fallback nếu user null (test trước, sau khi có token thì bỏ)
            String userId = (user != null) ? user.getUserId() : null;
            if (userId == null) {
                log.warn("No user from token, skipping ownership check");
            } else if (!Objects.equals(booking.getUserId(), userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
            }

            // Gọi ZaloPayService với appUser = userId (nếu null, service sẽ handle)
            Map<String, Object> order = zaloPayService.createOrder(booking, userId);
            log.info("ZaloPay order response: " + order); // Log để debug return_code

            int rc = safeInt(order.get("return_code"), 0);
            if (rc != 1) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "ZP_CREATE_FAILED", "details", order));
            }

            // ✅ lấy orderUrl & token theo nhiều key (phòng ZP đổi tên trường)
            String orderUrl = firstNonNullStr(
                    order.get("order_url"), order.get("orderurl"),
                    order.get("deeplink"), order.get("orderurl_web")
            );
            String zpToken = firstNonNullStr(order.get("zp_trans_token"), order.get("zp_trans_id_token"));

            return ResponseEntity.ok(Map.of(
                    "orderUrl", orderUrl,
                    "zpTransToken", zpToken,
                    "bookingId", bookingId
            ));
        } catch (Exception ex) {
            log.error("Create ZaloPay order failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", ex.getMessage()));
        }
    }

    private String firstNonNullStr(Object... arr) {
        for (Object o : arr) {
            if (o != null) {
                String s = String.valueOf(o);
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private int safeInt(Object obj, int defaultValue) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // 3) IPN callback từ ZaloPay (public)
    @PostMapping(value = "/payments/zalopay/ipn", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> ipn(@RequestBody Map<String, String> req) {
        log.info("=== ZALOPAY IPN RECEIVED ===");
        log.info("Request data: {}", req);

        try {
            String data = req.get("data");
            String mac = req.get("mac");

            if (data == null || mac == null) {
                log.warn("IPN missing data or mac: {}", req);
                return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "missing_params"));
            }

            // GỌI SERVICE XỬ LÝ
            ticketService.handleZpIpn(req);

            log.info("IPN PROCESSED SUCCESSFULLY: appTransId={}", req.get("app_trans_id"));
            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "success"));

        } catch (Exception e) {
            log.error("IPN PROCESSING FAILED", e);
            // ZALOPAY YÊU CẦU RETURN_CODE=1 DÙ CÓ ERROR
            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "error: " + e.getMessage()));
        }
    }

    // 4) API cho app gọi để confirm booking khi quay về từ ZaloPay
    @PostMapping("/bookings/{bookingId}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable String bookingId,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @AuthenticationPrincipal JwtUser user) {

        try {
            if (user == null || user.getUserId() == null) {
                // FOR AUTO CONFIRM FROM HTML - KHÔNG CẦN AUTH
                log.warn("Confirm without auth for bookingId={}", bookingId);
            }

            Ticket confirmed = ticketService.confirmBookingFromPending(bookingId,
                    user != null ? user.getUserId() : "auto-confirm");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("bookingId", confirmed.getId());
            result.put("bookingCode", confirmed.getBookingCode());
            result.put("status", confirmed.getStatus()); // CONFIRMED

            log.info("Auto confirmed booking: {}", bookingId);
            return ResponseEntity.ok(result);

        } catch (ResponseStatusException ex) {
            log.warn("Confirm failed: {}", ex.getReason());
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("success", false, "error", ex.getReason()));
        } catch (Exception ex) {
            log.error("Confirm error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "INTERNAL_ERROR"));
        }
    }

    // 4.1) API cho app gọi ngay khi quay về từ ZaloPay để kiểm tra trạng thái
    @GetMapping("/payments/zalopay/status/{bookingId}")
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(
            @PathVariable String bookingId,
            @AuthenticationPrincipal JwtUser user) {

        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // ✅ BỎ bắt buộc JWT: chỉ chặn nếu có user và KHÁC chủ vé
        boolean isAdmin = user != null && "ADMIN".equalsIgnoreCase(user.getRole());
        String reqUserId = (user != null ? user.getUserId() : null);
        boolean allowed = isAdmin || reqUserId == null || b.getUserId() == null || Objects.equals(b.getUserId(), reqUserId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingId", b.getId());
        result.put("bookingCode", b.getBookingCode());
        result.put("status", b.getStatus());
        // Nếu CONFIRMED, trả thêm info
        if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            result.put("seats", b.getSeats());
            result.put("showtimeId", b.getShowtimeId());
            result.put("amount", b.getAmount());
        }
        return ResponseEntity.ok(result);
    }

    // 5) Hủy vé + refund (ADMIN hoặc chủ vé, tùy policy)
    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @AuthenticationPrincipal JwtUser user) {
        try {
            String reason = body != null ? (String) body.getOrDefault("reason", "") : "";

            // Check ownership
            Ticket b = ticketRepo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

            boolean isAdmin = user != null && "ADMIN".equalsIgnoreCase(user.getRole());
            if (!isAdmin && b.getUserId() != null && !Objects.equals(b.getUserId(), user.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING");
            }

            Ticket canceled = ticketService.cancelBooking(id, reason);
            return ResponseEntity.ok(Map.of(
                    "bookingId", canceled.getId(),
                    "status", canceled.getStatus(),
                    "message", "Booking canceled successfully"
            ));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cancel booking failed for id={}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "CANCEL_FAILED", "message", ex.getMessage()));
        }
    }

    // 6) Cập nhật zpReturn để gọi status check
    @GetMapping(value = "/payments/zalopay/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> zpReturn(@RequestParam(required = false) String bookingId,
                                           @RequestParam(required = false) String canceled) {

        if (deeplinkBase == null || deeplinkBase.isBlank()) {
            return ResponseEntity.ok("Thiếu app.deeplink. Vui lòng mở lại ứng dụng.");
        }

        String sep = deeplinkBase.contains("?") ? "&" : "?";
        String target = deeplinkBase + sep + "bookingId=" + bookingId;

        if (canceled != null && !canceled.isEmpty()) {
            target += (target.contains("?") ? "&" : "?") + "canceled=1";
            return ResponseEntity.ok(createCancelHtml(target));
        }

        // CHECK STATUS VÀ CONFIRM TỰ ĐỘNG
        String statusCheckUrl = ensureNoTrailingSlash(publicBaseUrl) + "/api/payments/zalopay/status/" + bookingId;
//        String confirmUrl = ensureNoTrailingSlash(publicBaseUrl) + "/api/bookings/" + bookingId + "/confirm";
        String confirmUrl = ensureNoTrailingSlash(publicBaseUrl) + "/api/bookings/" + bookingId + "/confirm";

        String html = """
                <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Xác nhận thanh toán</title>
                <script>
                         async function sleep(ms){return new Promise(r=>setTimeout(r,ms));}
                         async function processPayment(){
                           const statusUrl = '%s';
                           const confirmUrl = '%s';
                           const target = '%s';
                
                           for (let i=0;i<5;i++){
                             try{
                               const rs = await fetch(statusUrl);       // check
                               if (rs.ok) {
                                 const s = await rs.json();
                                 if (s.status === 'CONFIRMED') { location.replace(target+'&status=SUCCESS'); return; }
                                 if (s.status === 'FAILED' || s.status === 'CANCELED') { location.replace(target+'&status=FAILED'); return; }
                                 if (s.status === 'PENDING_PAYMENT') {
                                   // ✅ FORCE CONFIRM (cách C)
                                   await fetch(confirmUrl, { method:'POST' });
                                 }
                               }
                             }catch(e){}
                             await sleep(1000);
                           }
                           // PENDING -> để app polling
                           location.replace(target+'&status=PENDING');
                         }
                         setTimeout(processPayment, 300);
                         </script>
                <style>body{font-family:Arial;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#111;color:#fff}</style>
                </head><body><div><div style="text-align:center">
                <div style="width:38px;height:38px;border:3px solid #777;border-top-color:#fff;border-radius:50%%;animation:spin 1s linear infinite;margin:0 auto 16px"></div>
                <h3>Đang xác nhận thanh toán...</h3><p>Vui lòng đợi trong giây lát</p></div></div>
                <style>@keyframes spin{to{transform:rotate(360deg)}}</style>
                </body></html>
                """.formatted(statusCheckUrl, target, target);

        return ResponseEntity.ok().body(html);
    }

    private String createCancelHtml(String target) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Thanh toán bị hủy</title>
                    <meta http-equiv="refresh" content="2;url=%s">
                </head>
                <body style="display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f5f5;">
                    <div style="text-align:center;padding:20px;background:white;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                        <h3>❌ Thanh toán đã bị hủy</h3>
                        <p>Đang quay về ứng dụng...</p>
                    </div>
                </body>
                </html>
                """.formatted(target);
    }

    // Helper method
    private String ensureNoTrailingSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}