//package com.ntth.spring_boot_heroku_cinema_app.controller;
//
//import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
//import com.ntth.spring_boot_heroku_cinema_app.service.MomoService;
//import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpStatus;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.server.ResponseStatusException;
//
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api")
//public class MomoController {
//    private static final Logger log = LoggerFactory.getLogger(MomoController.class);
//
//    private final TicketService ticketService;
//    private final MomoService momoService;
//
//    public MomoController(TicketService ticketService, MomoService momoService) {
//        this.ticketService = ticketService;
//        this.momoService = momoService;
//    }
//
//    // 1) Tạo booking MoMo (status=PENDING_PAYMENT)
//    @PostMapping("/bookings/momo")
//    @ResponseStatus(HttpStatus.CREATED)
//    @Transactional
//    public Map<String, Object> createBookingMoMo(@RequestBody Map<String, Object> body,
//                                                 @AuthenticationPrincipal JwtUser user) {
//        String holdId = body == null ? null : String.valueOf(body.get("holdId"));
//        if (holdId == null || holdId.isBlank()) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_HOLD_ID");
//        }
//        var t = ticketService.createBookingMoMo(holdId, user);
//
//        Map<String, Object> res = new LinkedHashMap<>();
//        res.put("bookingId", t.getId());
//        res.put("bookingCode", t.getBookingCode());
//        res.put("status", t.getStatus());
//        try { res.put("amount", t.getAmount()); } catch (Throwable ignore) {}
//        return res;
//    }
//
//    // 2) Tạo đơn thanh toán MoMo -> trả payUrl/deeplink
//    @PostMapping("/payments/momo/create")
//    public Map<String, Object> createMomoOrder(@RequestBody Map<String, Object> body,
//                                               @AuthenticationPrincipal JwtUser user) {
//        String bookingId = body == null ? null : String.valueOf(body.get("bookingId"));
//        if (bookingId == null || bookingId.isBlank()) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_BOOKING_ID");
//        }
//        return momoService.createOrder(bookingId, user);
//    }
//
//    // 3) IPN MoMo
//    // MoMo gửi JSON gồm: resultCode, orderId (ta dùng bookingCode), amount, transId, signature, ...
//    @PostMapping("/payments/momo/ipn")
//    public Map<String, Object> momoIpn(@RequestBody Map<String, Object> ipn) {
//        // Parse an toàn kiểu dữ liệu
//        String bookingCode = safeStr(ipn.get("orderId"));       // ta set = bookingCode khi tạo đơn
//        long amount = safeLong(ipn.get("amount"));
//        int resultCode = safeInt(ipn.get("resultCode"));         // 0 = success
//        String transId = safeStr(ipn.get("transId"));            // có thể là số lớn -> để String
//
//        if (bookingCode == null || bookingCode.isBlank()) {
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_ORDER_ID");
//        }
//
//        if (resultCode == 0) {
//            ticketService.handleMomoIpnPaid(null, bookingCode, amount, transId, ipn);
//        } else {
//            ticketService.handleMomoIpnFailed(null, bookingCode, transId, ipn);
//        }
//
//        Map<String, Object> ok = new LinkedHashMap<>();
//        ok.put("return_code", 1);
//        ok.put("return_message", "success");
//        return ok;
//    }
//
//    // ---------- Helpers ----------
//    private static String safeStr(Object o) {
//        return o == null ? null : String.valueOf(o);
//    }
//    private static long safeLong(Object o) {
//        if (o == null) return 0L;
//        try { return Long.parseLong(String.valueOf(o)); } catch (Throwable ignore) { return 0L; }
//    }
//    private static int safeInt(Object o) {
//        if (o == null) return -1;
//        try { return Integer.parseInt(String.valueOf(o)); } catch (Throwable ignore) { return -1; }
//    }
//}
