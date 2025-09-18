package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ZaloPayController {
    private final TicketService ticketService;

    @Value("${app.deeplink:}")
    private String deeplinkBase; // ví dụ: "myapp://zp-callback"

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
                    // ZP gửi JSON: { "data": "...", "mac": "..." }
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String,Object> json = om.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    if (json.get("data") != null)    params.put("data", String.valueOf(json.get("data")));
                    if (json.get("mac")  != null)    params.put("mac",  String.valueOf(json.get("mac")));
                } else {
                    // Thử parse tay kiểu form-urlencoded trong body
                    String[] pairs = body.split("&");
                    for (String p : pairs) {
                        int idx = p.indexOf('=');
                        if (idx > 0) {
                            String k = java.net.URLDecoder.decode(p.substring(0, idx), java.nio.charset.StandardCharsets.UTF_8);
                            String v = java.net.URLDecoder.decode(p.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8);
                            params.put(k, v);
                        }
                    }
                }
            }

            // 2) Lấy bookingId từ embed_data bên trong "data" nếu có, rồi NHÉT vào params
            try {
                String data = params.get("data");
                if (data != null) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
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
    @GetMapping(value = "/payments/zalopay/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> zpReturn(@RequestParam(required=false) String bookingId,
                                           @RequestParam(required=false) String canceled) {
        if (deeplinkBase == null || deeplinkBase.isBlank()) {
            return ResponseEntity.ok("<html><body>Thiếu app.deeplink. Vui lòng mở lại ứng dụng.</body></html>");
        }
        String sep = deeplinkBase.contains("?") ? "&" : "?";
        String target = deeplinkBase + (bookingId!=null? sep+"bookingId="+bookingId : "");
        if (canceled!=null) target += (target.contains("?")?"&":"?") + "canceled=1";

        String html = "<!doctype html><meta http-equiv='refresh' content='0;url="+target+"'>"
                + "<a href='"+target+"'>Mở ứng dụng</a>";
        return ResponseEntity.ok(html);
    }

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
}

