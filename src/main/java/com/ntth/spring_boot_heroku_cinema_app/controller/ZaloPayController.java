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
    @PostMapping("/payments/zalopay/ipn")
    public ResponseEntity<Map<String,Object>> ipn(@RequestParam Map<String,String> form) {
        try {
            // GIỮ NGUYÊN FLOW: chỉ bổ sung bookingId vào form cho TicketService.handleZpIpn(...)
            // để service hiện tại của bạn không cần sửa.
            String data = form.get("data"); // ZP post form-urlencoded: data + mac
            if (data != null) {
                // data có thể là JSON chứa embed_data (string hoặc object)
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String,Object> dataJson = om.readValue(data, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});
                Object embedObj = dataJson.get("embed_data");
                java.util.Map<String,Object> embed;
                if (embedObj instanceof String) {
                    embed = om.readValue((String) embedObj, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){});
                } else if (embedObj instanceof java.util.Map) {
                    embed = (java.util.Map<String,Object>) embedObj;
                } else {
                    embed = java.util.Collections.emptyMap();
                }
                String bookingId = (embed == null) ? null : String.valueOf(embed.get("bookingId"));
                if (bookingId != null && !bookingId.isBlank() && !form.containsKey("bookingId")) {
                    // NHÉT bookingId vào form để service hiện tại của bạn lấy bằng form.get("bookingId")
                    form.put("bookingId", bookingId);
                }
            }

            // GỌI LẠI service IPN hiện có của bạn (KHÔNG ĐỔI)
            ticketService.handleZpIpn(form);

            // ZaloPay cần return_code=1 để biết bạn đã nhận
            return ResponseEntity.ok(java.util.Map.of("return_code", 1, "return_message", "success"));
        } catch (Exception e) {
            // Không thay đổi retry policy: vẫn trả 1 để ZP không spam (tuỳ bạn)
            return ResponseEntity.ok(java.util.Map.of("return_code", 1, "return_message", "error"));
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

