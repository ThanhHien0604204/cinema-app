package com.ntth.spring_boot_heroku_cinema_app.controller;

import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import com.ntth.spring_boot_heroku_cinema_app.repositoryImpl.CustomUserDetails;
import com.ntth.spring_boot_heroku_cinema_app.service.TicketService;
import com.ntth.spring_boot_heroku_cinema_app.service.ZaloPayService;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public ResponseEntity<?> getMyTickets(
            Authentication auth,
            @RequestParam(defaultValue = "CONFIRMED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("GET /api/bookings/me - page: {}, size: {}, status: {}", page, size, status);

            // Kiểm tra authentication
            if (auth == null || !auth.isAuthenticated()) {
                log.warn("Unauthenticated access to /api/bookings/me");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Lấy userId từ JwtUser
            String userId = null;
            Object principal = auth.getPrincipal();

            if (principal instanceof JwtUser) {
                userId = ((JwtUser) principal).getUserId();
                log.debug("UserId from JwtUser: {}", userId);
            } else if (principal instanceof String) {
                // Fallback cho username (email)
                userId = (String) principal;
                log.debug("UserId from String principal: {}", userId);
            } else {
                log.warn("Unknown principal type: {}", principal != null ? principal.getClass() : "null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (userId == null || userId.isEmpty()) {
                log.warn("Cannot extract userId from authentication");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Ticket> tickets = ticketRepo.findByUserIdAndStatus(userId, status, pageable);

            log.info("Found {} tickets for user: {}", tickets.getTotalElements(), userId);
            return ResponseEntity.ok(tickets);

        } catch (Exception e) {
            log.error("Error in getMyTickets: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
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
        if (me == null) return ResponseEntity.status(401).build();
        String userId = me.getUserId(); // hoặc getId() tuỳ JwtUser của bạn

        if (movieId == null || movieId.isBlank()) {
            return ResponseEntity.ok(ticketRepo.findByUserIdOrderByCreatedAtDesc(userId));
        }

        List<String> showtimeIds = findShowtimeIdsByMovieId(movieId);
        if (showtimeIds.isEmpty()) return ResponseEntity.ok(List.of());

        List<Ticket> out = ticketRepo.findByUserIdAndShowtimeIdInOrderByCreatedAtDesc(userId, showtimeIds);
        return ResponseEntity.ok(out);
    }

    /** Hàm trợ giúp: lấy danh sách _id của collection "showtime" theo movieId */
    private List<String> findShowtimeIdsByMovieId(String movieId) {
        Query q = new Query(Criteria.where("movieId").is(movieId));
        q.fields().include("_id"); // chỉ cần _id cho nhanh
        // nếu collection của bạn tên "showtimes" thì đổi chuỗi bên dưới
        List<Document> docs = mongo.find(q, Document.class, "showtime");
        return docs.stream()
                .map(d -> ((org.bson.Document) d).get("_id"))
                .filter(Objects::nonNull)
                .map(id -> (id instanceof ObjectId oid) ? oid.toHexString() : id.toString())
                .collect(Collectors.toList());
    }
}