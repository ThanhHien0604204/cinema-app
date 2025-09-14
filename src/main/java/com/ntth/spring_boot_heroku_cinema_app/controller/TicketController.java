package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/bookings")
public class TicketController {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketRepository ticketRepo;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Phương án B: đặt vé trực tiếp (CASH: confirm ngay; ZALOPAY: PENDING_PAYMENT)
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal JwtUser principal) {
        String holdId = body.get("holdId");
        String method = body.getOrDefault("paymentMethod", "VNPAY");
        Ticket b = ticketService.createBookingFromHold(holdId, principal.getUserId(), method);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "bookingId", b.getId(),
                "bookingCode", b.getBookingCode(),
                "status", b.getStatus(),
                "amount", b.getAmount()
        ));
    }

    /**
     * Lấy trạng thái 1 booking để app poll
     */
    @GetMapping("/{id}")
    public Map<String, Object> getOne(@PathVariable String id,
                                      @AuthenticationPrincipal JwtUser user) {
        // Dùng Repository – Spring Data tự map _id (ObjectId) từ String
        Ticket b = ticketRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // Chỉ cho chủ vé (nếu bạn có role ADMIN thì nới lỏng ở đây)
        boolean isAdmin = user != null && "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && b.getUserId() != null && !Objects.equals(b.getUserId(), user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", b.getId());
        res.put("bookingId", b.getId());
        res.put("bookingCode", b.getBookingCode());
        res.put("status", b.getStatus());
        res.put("showtimeId", b.getShowtimeId());
        try { res.put("amount", b.getAmount()); } catch (Throwable ignore) {}
        res.put("seats", b.getSeats());
        try {
            Map<String, Object> pay = new LinkedHashMap<>();
            if (b.getPayment() != null && b.getPayment().getGateway() != null) {
                pay.put("gateway", b.getPayment().getGateway());
            }
            res.put("payment", pay);
        } catch (Throwable ignore) {}
        return res;
    }

    // (tuỳ chọn) lấy theo bookingCode – tiện cho CSKH
    @GetMapping("/code/{code}")
    public Map<String, Object> getByCode(@PathVariable String code,
                                         @AuthenticationPrincipal JwtUser user) {
        Ticket b = ticketRepo.findByBookingCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        boolean isAdmin = user != null && "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && b.getUserId() != null && !Objects.equals(b.getUserId(), user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", b.getId());
        res.put("bookingId", b.getId());
        res.put("bookingCode", b.getBookingCode());
        res.put("status", b.getStatus());
        res.put("showtimeId", b.getShowtimeId());
        try { res.put("amount", b.getAmount()); } catch (Throwable ignore) {}
        res.put("seats", b.getSeats());
        return res;
    }
}
