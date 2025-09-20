package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.service.MomoService;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/momo")
public class MomoController {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final MomoService momo;
    private final TicketService ticketService;
    private final ObjectMapper om = new ObjectMapper();

    public MomoController(MomoService momo, TicketService ticketService) {
        this.momo = momo; this.ticketService = ticketService;
    }

    // FE gọi: { bookingId }
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Map<String,String> body, JwtUser me) {
        String bookingId = body.get("bookingId");
        Map<String,Object> out = momo.createOrder(bookingId, me.getUserId());
        return ResponseEntity.ok(out);
    }

    // MoMo gọi IPN (server->server)
    @PostMapping("/ipn")
    @Transactional
    public ResponseEntity<?> ipn(@RequestBody Map<String,Object> ipn) {
        // MoMo gửi rất nhiều field; cần verify chữ ký
        try {
            ticketService.handleMomoIpn(ipn);
            Map<String,Object> ok = new LinkedHashMap<>();
            ok.put("resultCode", 0);
            ok.put("message", "success");
            return ResponseEntity.ok(ok);
        } catch (Throwable e) {
            log.warn("MoMo IPN error: {}", e.getMessage());
            Map<String,Object> err = new LinkedHashMap<>();
            err.put("resultCode", 1);
            err.put("message", "error");
            return ResponseEntity.ok(err); // MoMo thích 200 kèm lỗi logic
        }
    }

    // Optional: người dùng quay về sau khi thanh toán
    // Nên chỉ hiển thị thông báo/redirect deep link app
    @GetMapping("/return")
    public ResponseEntity<?> ret(@RequestParam Map<String,String> q) {
        // Có thể trả HTML redirect về app deep link: myapp://momo-callback?bookingId=...
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("message", "OK");
        res.put("params", q);
        return ResponseEntity.ok(res);
    }
}
