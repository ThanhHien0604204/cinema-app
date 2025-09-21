package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Showtime;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.ShowtimeRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class TicketController {
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketRepository ticketRepo;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ShowtimeRepository showtimeRepo;

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    public TicketController(TicketRepository ticketRepo, MongoTemplate mongo) {
        this.ticketRepo = ticketRepo;
        this.mongo = mongo;
    }

    // Phương án B: đặt vé trực tiếp (CASH: confirm ngay; ZALOPAY: PENDING_PAYMENT)
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal JwtUser principal) {
        String holdId = body.get("holdId");
        String method = body.getOrDefault("paymentMethod", "CASH");
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

    @GetMapping("/me")
    public ResponseEntity<Page<Ticket>> getMyTickets(
            Authentication auth,  // Lấy userId từ JwtUser
            @RequestParam(defaultValue = "CONFIRMED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String userId = ((JwtUser) auth.getPrincipal()).getUserId();  // Từ JwtFilter
        Pageable pageable = PageRequest.of(page, size);
        Page<Ticket> tickets = ticketRepo.findByUserIdAndStatus(userId, status, pageable);
        return ResponseEntity.ok(tickets);  // Trả Page<Ticket> với nested PaymentInfo
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
    //Lấy vé của CHÍNH TÔI
    @GetMapping("/user/me")
    public ResponseEntity<List<Ticket>> getMyTickets(
            @RequestParam(value = "movieId", required = false) String movieId,
            @AuthenticationPrincipal JwtUser me
    ) {
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String userId = me.getUserId();

        if (movieId == null || movieId.isBlank()) {
            return ResponseEntity.ok(ticketRepo.findByUserIdOrderByCreatedAtDesc(userId));
        }

        List<String> showtimeIds = findShowtimeIdsByMovieId(movieId); // helper dưới
        if (showtimeIds.isEmpty()) return ResponseEntity.ok(List.of());

        return ResponseEntity.ok(
                ticketRepo.findByUserIdAndShowtimeIdInOrderByCreatedAtDesc(userId, showtimeIds)
        );
    }

    private List<String> findShowtimeIdsByMovieId(String movieId) {
        List<String> showtimeIds = showtimeRepo.findByMovieId(movieId)
                .stream().map(Showtime::getId).toList();
        return showtimeIds;
    }
}