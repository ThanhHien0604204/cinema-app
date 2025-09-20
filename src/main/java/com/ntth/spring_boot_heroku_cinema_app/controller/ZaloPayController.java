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

            // Validate booking exists and belongs to user
            Ticket booking = ticketRepo.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

            if (!Objects.equals(booking.getUserId(), user.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING");
            }

            if (!"PENDING_PAYMENT".equalsIgnoreCase(booking.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_STATUS");
            }

            String appUser = user.getUserId(); // hoặc email/phone tùy config
            Map<String, Object> orderResponse = zaloPayService.createOrder(booking, appUser);

            return ResponseEntity.ok(Map.of(
                    "orderUrl", orderResponse.get("order_url"),
                    "zpTransToken", orderResponse.get("zp_trans_token"),
                    "bookingId", bookingId
            ));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Create ZaloPay order failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ORDER_CREATION_FAILED", "message", ex.getMessage()));
        }
    }

    // 3) IPN callback từ ZaloPay (public)
    @PostMapping(value = "/payments/zalopay/ipn", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> ipn(@RequestBody Map<String, String> req) {
        try {
            String data = req.get("data");
            String mac = req.get("mac");

            if (data == null || mac == null) {
                log.warn("IPN missing data or mac");
                return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "missing_params"));
            }

            ticketService.handleZpIpn(req);
            log.info("IPN processed successfully for appTransId={}", req.get("app_trans_id"));

            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "success"));
        } catch (Exception e) {
            log.error("IPN processing failed", e);
            // ZaloPay yêu cầu return_code=1 dù có error
            return ResponseEntity.ok(Map.of("return_code", 1, "return_message", "error"));
        }
    }

    // 4) API cho app gọi để confirm booking khi quay về từ ZaloPay
    @PostMapping("/bookings/{bookingId}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable String bookingId,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @AuthenticationPrincipal JwtUser user) {

        log.info("Confirm booking request: bookingId={}, userId={}", bookingId,
                user != null ? user.getUserId() : "anonymous");

        try {
            if (user == null || user.getUserId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "Please login"));
            }

            // Gọi service để CONFIRM (ticket, seats, delete lock)
            Ticket confirmed = ticketService.confirmBookingFromPending(bookingId, user.getUserId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("bookingId", confirmed.getId());
            result.put("bookingCode", confirmed.getBookingCode());
            result.put("status", confirmed.getStatus()); // CONFIRMED
            result.put("seats", confirmed.getSeats());
            result.put("showtimeId", confirmed.getShowtimeId());
            result.put("amount", confirmed.getAmount());
            result.put("message", "Vé đã được xác nhận thành công!");

            log.info("Booking confirmed successfully: bookingId={}, code={}, user={}",
                    bookingId, confirmed.getBookingCode(), user.getUserId());

            return ResponseEntity.ok(result);

        } catch (ResponseStatusException ex) {
            log.warn("Confirm failed with status {}: {}", ex.getStatusCode(), ex.getReason());
            Map<String, Object> error = Map.of(
                    "success", false,
                    "error", ex.getReason(),
                    "statusCode", ex.getStatusCode().value()
            );
            return ResponseEntity.status(ex.getStatusCode()).body(error);
        } catch (Exception ex) {
            log.error("Unexpected error confirming booking {}: {}", bookingId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "INTERNAL_ERROR", "message", ex.getMessage()));
        }
    }

    // 4.1) API cho app gọi ngay khi quay về từ ZaloPay để kiểm tra trạng thái
    @GetMapping("/payments/zalopay/status/{bookingId}")
    public ResponseEntity<Map<String, Object>> checkPaymentStatus(
            @PathVariable String bookingId,
            @AuthenticationPrincipal JwtUser user) {

        try {
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
            result.put("status", b.getStatus());

            // THÊM LOGIC CHO PAYMENT STATUS
            Ticket.PaymentInfo pay = b.getPayment();
            if (pay != null) {
                Map<String, Object> paymentInfo = new LinkedHashMap<>();
                paymentInfo.put("gateway", pay.getGateway());
                if (pay.getTxId() != null) {
                    paymentInfo.put("txId", pay.getTxId());
                }
                if (pay.getPaidAt() != null) {
                    paymentInfo.put("paidAt", pay.getPaidAt().toString());
                }
                result.put("payment", paymentInfo);
            }

            // Nếu đã CONFIRMED, trả thêm thông tin ghế
            if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                result.put("seats", b.getSeats());
                result.put("showtimeId", b.getShowtimeId());
                result.put("amount", b.getAmount());
            }

            return ResponseEntity.ok(result);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Check payment status failed for bookingId={}", bookingId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "STATUS_CHECK_FAILED", "message", ex.getMessage()));
        }
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
    public ResponseEntity<String> zpReturn(@RequestParam(required=false) String bookingId,
                                           @RequestParam(required=false) String canceled) {
        if (deeplinkBase == null || deeplinkBase.isBlank()) {
            return ResponseEntity.ok("Thiếu app.deeplink. Vui lòng mở lại ứng dụng.");
        }

        String sep = deeplinkBase.contains("?") ? "&" : "?";
        String target = deeplinkBase + sep + "bookingId=" + bookingId;

        if (canceled != null) {
            target += (target.contains("?") ? "&" : "?") + "canceled=1";
            String html = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="display:flex;justify-content:center;align-items:center;height:100vh;margin:0;">
                <div style="text-align:center;padding:20px;background:white;border-radius:10px;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                    <h3>Thanh toán đã bị hủy</h3>
                    <p><a href="%s">Mở ứng dụng</a></p>
                </div>
            </body></html>
            """.formatted(target);
            return ResponseEntity.ok().body(html);
        }

        // REDIRECT NGAY VỀ APP - app sẽ handle confirm
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Xác nhận vé</title>
            <meta http-equiv="refresh" content="1;url=%s">
            <script>
                // Fallback redirect
                setTimeout(() => {
                    if (!window.location.href.includes('myapp://')) {
                        window.location.href = '%s';
                    }
                }, 1000);
            </script>
            <style>
                body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
                .container { text-align: center; padding: 40px; background: rgba(255,255,255,0.1); border-radius: 20px; backdrop-filter: blur(10px); }
                .icon { font-size: 48px; margin-bottom: 20px; }
                .spinner { border: 3px solid rgba(255,255,255,0.3); border-top: 3px solid white; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 0 auto 20px; }
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="spinner"></div>
                <div class="icon">🎫</div>
                <h2>Xác nhận vé</h2>
                <p>Đang chuyển về ứng dụng...</p>
                <p><small>Nếu không tự động chuyển, <a href="%s" style="color: #fff;">nhấn vào đây</a></small></p>
            </div>
        </body>
        </html>
        """.formatted(target, target, target);

        return ResponseEntity.ok().body(html);
    }

    // Helper method
    private String ensureNoTrailingSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}