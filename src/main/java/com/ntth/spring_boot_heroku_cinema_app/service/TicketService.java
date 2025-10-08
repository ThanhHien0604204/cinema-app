package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntth.spring_boot_heroku_cinema_app.filter.JwtUser;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLedger;
import com.ntth.spring_boot_heroku_cinema_app.pojo.SeatLock;
import com.ntth.spring_boot_heroku_cinema_app.pojo.Ticket;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLedgerRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.SeatLockRepository;
import com.ntth.spring_boot_heroku_cinema_app.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class TicketService {
    private final SeatLockRepository lockRepo;
    private final TicketRepository ticketRepo;
    private final SeatLedgerRepository ledgerRepo;
    private final ZaloPayService zalo;
    private final MongoTemplate mongo;
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    public TicketService(SeatLockRepository lockRepo,
                         TicketRepository bookingRepo,
                         SeatLedgerRepository ledgerRepo,
                         ZaloPayService zalo,
                         MongoTemplate mongo) {
        this.lockRepo = lockRepo;
        this.ticketRepo = bookingRepo;
        this.ledgerRepo = ledgerRepo;
        this.zalo = zalo;
        this.mongo = mongo;
    }
    /**
     * CONFIRM booking từ PENDING_PAYMENT khi user quay về app
     * - Set ticket status = CONFIRMED
     * - Update seat_ledger: status=CONFIRMED, refType=BOOKING, refId=ticketId
     * - Delete seat_lock tương ứng
     * - All in one transaction
     */

//    @Transactional
//    public Ticket confirmBookingFromPending(String bookingId, String userId) {
//        log.info("Confirming booking: {} by user: {}", bookingId, userId);
//
//        // 1. Find booking
//        Ticket b = ticketRepo.findById(bookingId)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));
//
//        // 2. Validate status
//        if (!"PENDING_PAYMENT".equalsIgnoreCase(b.getStatus())) {
//            if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
//                log.info("Booking {} already confirmed", bookingId);
//                return b;
//            }
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS: " + b.getStatus());
//        }
//
//        // 3. Check payment complete (txId from IPN)
//        Ticket.PaymentInfo pay = b.getPayment();
//        if (pay == null || pay.getTxId() == null) {
//            log.warn("No payment txId for booking {}", bookingId);
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_COMPLETED");
//        }
//
//        // 4. UPDATE TICKET → CONFIRMED
//        b.setStatus("CONFIRMED");
//        if (pay.getPaidAt() == null) {
//            pay.setPaidAt(Instant.now());
//        }
//        ticketRepo.save(b);
//        log.info("Ticket updated to CONFIRMED: {}", bookingId);
//
//        // 5. UPDATE SEAT_LEDGER → CONFIRMED
//        try {
//            List<String> seats = b.getSeats();
//            String showtimeId = b.getShowtimeId();
//
//            if (seats != null && !seats.isEmpty() && showtimeId != null) {
//                long updatedSeats = updateSeatsToConfirmed(showtimeId, seats, bookingId);
//                log.info("Updated {} seats to CONFIRMED for booking {}", updatedSeats, bookingId);
//            }
//        } catch (Exception e) {
//            log.error("Seat update failed for booking {}: {}", bookingId, e.getMessage());
//            // Không throw để tránh break IPN flow
//        }
//
//        // 6. DELETE SEAT_LOCK
//        try {
//            String holdId = b.getHoldId();
//            if (holdId != null && !holdId.isEmpty()) {
//                lockRepo.deleteById(holdId);
//                log.info("Deleted hold for booking: {}", holdId);
//            }
//        } catch (Exception e) {
//            log.warn("Failed to delete hold for booking {}: {}", bookingId, e.getMessage());
//        }
//
//        log.info("Booking {} CONFIRMED successfully", bookingId);
//        return b;
//    }
    @Transactional
    public Ticket confirmBookingFromPending(String bookingId, String userId) {
        log.info("FORCE CONFIRM booking: {} by user: {}", bookingId, userId);

        // 1) Load ticket
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // 2) Idempotent
        if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            log.info("Booking {} already confirmed", bookingId);
            return b;
        }
//        if (!"PENDING_PAYMENT".equalsIgnoreCase(b.getStatus())) {
//            // Cho phép confirm cả khi FAILED/CANCELED? -> tuỳ bạn.
//            // Mặc định chặn để tránh confirm nhầm:
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS: " + b.getStatus());
//        }

        // 3) KHÔNG kiểm tra txId / IPN. Xác nhận NGAY.
        Ticket.PaymentInfo pay = b.getPayment();
        if (pay == null) {
            pay = new Ticket.PaymentInfo();
            b.setPayment(pay);
        }
        if (pay.getGateway() == null || pay.getGateway().isBlank()) {
            pay.setGateway("ZALOPAY"); // hoặc giữ nguyên nếu đã set.
        }
        pay.setPaidAt(Instant.now());
        // Gắn flag để biết đây là force confirm (phục vụ audit/CSKH)
        Map<String, Object> raw = pay.getRaw();
        if (raw == null) raw = new HashMap<>();
        raw.put("forceConfirm", true);
        raw.put("forceBy", (userId == null ? "auto-confirm" : userId));
        raw.put("forceAt", Instant.now().toString());
        pay.setRaw(raw);

        // 4) TICKET -> CONFIRMED
        b.setStatus("CONFIRMED");
        ticketRepo.save(b);
        log.info("Ticket {} set to CONFIRMED (force)", bookingId);

        // 5) UPDATE SEAT_LEDGER -> CONFIRMED (từ HOLD)
        try {
            List<String> seats = b.getSeats();
            String showtimeId = b.getShowtimeId();
            String holdId = b.getHoldId();
            if (seats != null && !seats.isEmpty() && showtimeId != null && holdId != null && !holdId.isBlank()) {
                try {
                    long updated = ledgerRepo.confirmMany(showtimeId, seats, b.getId(), holdId);
                    log.info("ledger.confirmMany updated={}, ticket={}", updated, b.getId());
                } catch (Exception e) {
                    log.warn("ledger.confirmMany failed for booking {}: {}", b.getId(), e.toString());
                }
                // 3) Xoá hold sau khi confirm
                try { lockRepo.deleteById(holdId); } catch (Exception ignore) {}
            } else {
                log.warn("Missing showtimeId/seats when confirming booking {}", bookingId);
            }
        } catch (Exception e) {
            log.error("Seat update failed for booking {}: {}", bookingId, e.getMessage());
        }

//        // 6) DELETE SEAT_LOCK (nếu có)
//        try {
//            String holdId = b.getHoldId();
//            if (holdId != null && !holdId.isBlank()) {
//                lockRepo.deleteById(holdId);
//                log.info("Deleted hold {} for booking {}", holdId, bookingId);
//            }
//        } catch (Exception e) {
//            log.warn("Failed to delete hold for booking {}: {}", bookingId, e.getMessage());
//        }

        return b;
    }

    /**
     * Update seats từ HOLD → CONFIRMED using MongoTemplate
     */
    private long updateSeatsToConfirmed(String showtimeId, List<String> seats, String ticketId, String holdId) {
        if (showtimeId == null || showtimeId.isBlank() || seats == null || seats.isEmpty()) return 0L;
        // Build query: find seats with status HOLD và refType LOCK
        Query query = new Query();
        query.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        query.addCriteria(Criteria.where("seat").in(seats));

        // ✅ Dùng "state" thay vì "status" (và idempotent: chấp nhận cả trạng thái đã CONFIRMED)
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("status").is("HOLD").and("refType").is("LOCK").and("refId").is(holdId),
                Criteria.where("status").is("HOLD").and("refType").is("LOCK"),            // phòng khi refId chưa set
                Criteria.where("status").is("CONFIRMED").and("refType").is("BOOKING")      // idempotent
                        .and("refId").is(ticketId)
        ));

        // Count seats cần update để validate
        long totalSeats = mongo.count(query, SeatLedger.class);
        if (totalSeats == 0) {
            log.warn("Không tìm thấy chỗ ngồi GIỮ để xác nhận showtimeId={}, seats={}", showtimeId, seats);
            return 0;
        }

        // Build update: set CONFIRMED, refType=BOOKING, refId=ticketId, remove expiresAt
        Update update = new Update();
        update.set("status", "CONFIRMED");
        update.set("refType", "BOOKING");
        update.set("refId", ticketId);
        update.unset("expiresAt");

        // FIX: Dùng updateMulti với đúng signature
        // updateMulti(Query query, Update update, Class<T> entityClass)
        long updated = mongo.updateMulti(query, update, "seat_ledger").getModifiedCount();

        log.debug("Updated {} seats to CONFIRMED (expected: {}) for ticket={}", updated, totalSeats, ticketId,holdId);
        return updated;
    }

    /**
     * Lock seats từ FREE → HOLD using MongoTemplate
     */
    @Transactional
    public long lockSeatsFromFree(String showtimeId, List<String> seats, String holdId, Instant expiresAt) {
        // Build query cho seats available
        Query query = new Query();
        query.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        query.addCriteria(Criteria.where("seatNumber").in(seats));
        query.addCriteria(Criteria.where("status").is("FREE"));
        // HOẶC HOLD đã expired
        query.addCriteria(Criteria.where("status").is("HOLD").and("expiresAt").lt(expiresAt));

        // Count seats available
        long totalSeats = mongo.count(query, SeatLedger.class);

        // Build update
        Update update = new Update();
        update.set("status", "HOLD");
        update.set("refType", "LOCK");
        update.set("refId", holdId);
        update.set("expiresAt", expiresAt);

        // FIX: updateMulti với đúng signature
        long updated = mongo.updateMulti(query, update, SeatLedger.class).getModifiedCount();

        if (updated != totalSeats) {
            log.warn("Lock seats partial success: updated={} expected={} for holdId={}",
                    updated, totalSeats, holdId);
        }

        return updated;
    }

    /**
     * Free confirmed seats
     */
    @Transactional
    private long freeConfirmedSeats(String showtimeId, List<String> seats, String bookingId) {
        Query query = new Query(Criteria.where("showtimeId").is(showtimeId)
                .and("seatNumber").in(seats)
                .and("status").is("CONFIRMED")
                .and("refType").is("BOOKING")
                .and("refId").is(null));
        Update update = new Update().set("status", "FREE")
                .set("refType", null)
                .set("refId", null)
                .unset("expiresAt");
        return mongo.updateMulti(query, update, "seat_ledger").getModifiedCount();
    }
    /**
     * Tạo booking từ 1 hold có sẵn:
     * - Kiểm tra hold còn hạn
     * - Tạo booking (PENDING_PAYMENT hoặc CONFIRMEDnếu CASH)
     * - Chuyển ledger HOLD->CONFIRMED atomically (nếu paymentMethod=CASH)
     *
     * @param holdId id của seat lock
     * @param userId chủ sở hữu
     * @param method payment method
     * @return booking mới tạo
     */
    /**
     * Tạo booking từ 1 hold có sẵn (giữ nguyên logic cũ)
     */
    @Transactional
    public Ticket createBookingFromHold(String holdId, String userId, String method) {
        log.info("Creating booking from holdId: " + holdId + ", userId: " + userId + ", method: " + method);
        // 1. Kiểm tra hold
        SeatLock hold = lockRepo.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND"));

        if (!userId.equals(hold.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YOUR_HOLD");
        }

        Instant now = Instant.now();
        if (now.isAfter(hold.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED");
        }

        // 2. Tạo booking
        Ticket booking = new Ticket();
        booking.setUserId(userId);
        booking.setShowtimeId(hold.getShowtimeId());
        booking.setSeats(hold.getSeats());
        booking.setAmount(hold.getAmount());
        booking.setHoldId(holdId);
        booking.setCreatedAt(now);
        booking.setBookingCode(genCode());

        if ("CASH".equalsIgnoreCase(method)) {
            booking.setStatus("CONFIRMED");
            Ticket.PaymentInfo payment = new Ticket.PaymentInfo();
            payment.setGateway("CASH");
            booking.setPayment(payment);

            // Confirm ledger using MongoTemplate
            log.info("Confirming seats for showtime: " + hold.getShowtimeId() + ", seats: " + hold.getSeats() + ", bookingId: " + booking.getId());
            long updated = updateSeatsToConfirmed(hold.getShowtimeId(), hold.getSeats(), booking.getId(), holdId);
            if (updated != hold.getSeats().size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_CONFIRM_FAILED");
            }

            // Xóa hold
            lockRepo.deleteById(holdId);
        } else {
            booking.setStatus("PENDING_PAYMENT");
            Ticket.PaymentInfo payment = new Ticket.PaymentInfo();
            payment.setGateway(method);
            booking.setPayment(payment);
        }

        ticketRepo.save(booking);
        return booking;
    }

    /**
     * Tạo booking ZaloPay (PENDING_PAYMENT)
     * @param holdId hold ID
     * @param userId user ID
     * @return booking
     */
    @Transactional
    public Ticket createBookingZaloPay(String holdId, String userId) {
        var existed = findExistingByHold(holdId);
        if (existed.isPresent()) {
            var b = existed.get();
            // Nếu booking đã tạo rồi, trả về luôn để idempotent (tránh race CASH/ZP)
            if (!"CANCELED".equalsIgnoreCase(b.getStatus()) && !"FAILED".equalsIgnoreCase(b.getStatus())) {
                return b;
            }
        }
        return createBookingFromHold(holdId, userId, "ZALOPAY");
    }

    /**
     * Xử lý IPN từ ZaloPay
     * @param req request map
     */
    @Transactional
    public void handleZpIpn(Map<String, String> req) {
        log.info("=== IPN RECEIVED ===");
        log.info("Full request: {}", req);

        String data = req.get("data");
        String mac = req.get("mac");
        if (data == null || mac == null) {
            log.warn("IPN missing data or mac");
            return; // Không throw để tránh ZaloPay retry infinite
        }

        // VERIFY SIGNATURE
        Map<String, Object> verify = zalo.verifyIpn(data, mac);
        int returnCode = (int) verify.getOrDefault("return_code", -1);
        if (returnCode != 1) {
            log.error("IPN signature invalid: return_code={}", returnCode);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) verify.get("parsed");
        if (parsed == null) {
            log.error("IPN parse failed");
            return;
        }

        // EXTRACT BOOKING INFO
        String appTransId = String.valueOf(parsed.get("app_trans_id"));
        if (appTransId == null || !appTransId.contains("_")) {
            log.error("Invalid appTransId: {}", appTransId);
            return;
        }

        String bookingCode = appTransId.split("_", 2)[1];
        Ticket b = ticketRepo.findByBookingCode(bookingCode)
                .orElse(null);

        if (b == null) {
            log.error("Booking not found for code: {}", bookingCode);
            return;
        }

        // CHECK STATUS
        int statusFromZp = safeInt(parsed.get("status"), -1);
        log.info("IPN for booking {}: status={}, amount={}",
                b.getId(), statusFromZp, parsed.get("amount"));

        if (statusFromZp == 1) { // SUCCESS
            // UPDATE PAYMENT
            Ticket.PaymentInfo pay = b.getPayment();
            if (pay == null) {
                pay = new Ticket.PaymentInfo();
                pay.setGateway("ZALOPAY");
                b.setPayment(pay);
            }

            String zpTransId = String.valueOf(parsed.get("zp_trans_id"));
            pay.setTxId(zpTransId);
            pay.setZpTransId(zpTransId);
            pay.setRaw(parsed);

            // AUTO CONFIRM NGAY TRONG IPN
            b.setStatus("CONFIRMED");
            pay.setPaidAt(Instant.now());
            ticketRepo.save(b);

            // UPDATE SEATS
            updateSeatsToConfirmed(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());

            // DELETE HOLD
            if (b.getHoldId() != null) {
                lockRepo.deleteById(b.getHoldId());
            }

            log.info("IPN AUTO CONFIRMED booking: {} (code: {})", b.getId(), b.getBookingCode());

        } else if (statusFromZp == 2) { // FAILED
            b.setStatus("FAILED");
            ticketRepo.save(b);
            log.warn("IPN FAILED booking: {} (code: {})", b.getId(), b.getBookingCode());

        } else {
            log.info("IPN PENDING (status={}): booking {} - waiting", statusFromZp, b.getId());
        }
    }

    private void scheduleDelayedConfirm(String bookingId, String userId) {
        // Lên lịch confirm sau 5s nếu user không quay về app
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                .execute(() -> {
                    try {
                        log.info("Auto confirming booking after timeout: {}", bookingId);
                        confirmBookingFromPending(bookingId, userId);
                    } catch (Exception e) {
                        log.error("Delayed confirm failed for booking {}: {}", bookingId, e.getMessage());
                    }
                });
    }
    /**
     * Hủy booking và refund nếu cần
     * @param bookingId ticket ID
     * @param reason lý do
     * @return ticket canceled
     */
    @Transactional
    public Ticket cancelBooking(String bookingId, String reason) {
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        if ("CANCELED".equalsIgnoreCase(b.getStatus()) || "REFUNDED".equalsIgnoreCase(b.getStatus())) {
            return b;
        }

        // Refund nếu ZaloPay
        Ticket.PaymentInfo pay = b.getPayment();
        if (pay != null && "ZALOPAY".equalsIgnoreCase(pay.getGateway()) && pay.getTxId() != null) {
            String zpTransId = pay.getTxId();
            String cancelReason = reason != null && !reason.isBlank() ? reason : "User canceled";

            Map<String, Object> ret = zalo.refund(zpTransId, b.getAmount(), cancelReason);

            Object rcObj = (ret != null ? ret.get("return_code") : null);
            int rc;
            try { rc = (rcObj instanceof Number) ? ((Number) rcObj).intValue() : Integer.parseInt(String.valueOf(rcObj)); }
            catch (Exception e) { rc = -999; }

            if (rc != 1) {
                log.error("ZaloPay refund failed: rc={}, resp={}", rc, ret);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ZALO_REFUND_FAILED");
            }
        }

        // Free seats (CONFIRMED -> FREE)
        try {
            ledgerRepo.freeMany(b.getShowtimeId(), b.getSeats(), b.getId());
        } catch (Throwable e) {
            log.warn("freeMany failed for booking {}: {}", b.getId(), e.toString());
        }

        b.setStatus("CANCELED");
        ticketRepo.save(b);
        return b;
    }


    /**
     * Tạo mã ngẫu nhiên theo định dạng UIN-XXXXXX
     * @return Chuỗi mã ngẫu nhiên, ví dụ: UIN-8F3K2N
     */
    private String genCode() {
        // 1. Định nghĩa tập hợp ký tự để tạo mã ngẫu nhiên
        // Loại bỏ các ký tự dễ nhầm lẫn như I, O, 0, 1 để tránh lỗi đọc
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        // 2. Sử dụng ThreadLocalRandom để tạo số ngẫu nhiên hiệu quả
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // 3. Tạo chuỗi mã với tiền tố "UIN-" và 6 ký tự ngẫu nhiên
        StringBuilder sb = new StringBuilder("UIN-");
        for (int i = 0; i < 6; i++) {
            // Lấy một ký tự ngẫu nhiên từ alphabet
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        // 4. Trả về chuỗi mã hoàn chỉnh
        return sb.toString();
    }

    private int safeInt(Object obj, int defaultValue) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long safeLong(Object obj, long defaultValue) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // --------- Helper: tìm booking theo holdId (idempotent) ----------
    private Optional<Ticket> findExistingByHold(String holdId) {
        return ticketRepo.findByHoldId(holdId);
        // hoặc dùng findFirstByHoldIdOrderByCreatedAtDesc(holdId) nếu bạn đã thêm ở repo
    }

    // --------- Helper: load & validate SeatLock ----------
    private SeatLock loadValidHoldOrThrow(String holdId, String userId) {
        SeatLock lock = lockRepo.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND"));
        if (!Objects.equals(lock.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HOLD_NOT_OWNED");
        }
        if (lock.getExpiresAt() != null && Instant.now().isAfter(lock.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "HOLD_EXPIRED");
        }
        return lock;
    }

    // ================== Tạo booking CASH và xác nhận ngay ==================
    @Transactional
    public Ticket createBookingCash(String holdId, JwtUser user) {
        String userId = user.getUserId();

        // 1) Idempotent: nếu đã có booking cho holdId → trả về luôn
        Optional<Ticket> existed = findExistingByHold(holdId);
        if (existed.isPresent()) {
            return existed.get();
        }

        // 2) Validate hold thuộc user và còn hạn
        SeatLock lock = loadValidHoldOrThrow(holdId, userId);

        // 3) Tạo Ticket PENDING_PAYMENT (gateway = CASH)
        Ticket b = new Ticket();
        b.setUserId(userId);
        b.setBookingCode(genCode());
        b.setShowtimeId(lock.getShowtimeId());
        b.setSeats(new ArrayList<>(lock.getSeats()));
        // SeatLock của bạn có field Amount viết hoa, nhưng getter vẫn thường là getAmount()
        Long lockAmount = lock.getAmount(); // nếu IDE báo lỗi, đổi thành getAmount() đúng tên getter của bạn
        b.setAmount(lockAmount == null ? 0L : lockAmount);
        b.setStatus("PENDING_PAYMENT");
        b.setHoldId(holdId);
        b.setCreatedAt(Instant.now());

        Ticket.PaymentInfo p = b.getPayment();
        if (p == null) p = new Ticket.PaymentInfo();
        p.setGateway("CASH");
        if (p.getRaw() == null) p.setRaw(new HashMap<>());
        b.setPayment(p);

        try {
            b = ticketRepo.save(b);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Nếu 2 request đua nhau cùng holdId, quay lại lấy bản đã được insert
            return ticketRepo.findByHoldId(holdId).orElseThrow(() -> e);
        }

        // 4) Xác nhận ngay (giống IPN thành công)
        return finalizeConfirm(b.getId(), Instant.now(), /*transId*/ null, /*raw*/ Map.of("source", "CASH"));
    }

    // ================== (Tuỳ chọn) Tạo booking ZaloPay (idempotent theo hold) ==================
    @Transactional
    public Ticket createBookingZaloPay(String holdId, JwtUser user) {
        String userId = user.getUserId();

        Optional<Ticket> existed = findExistingByHold(holdId);
        if (existed.isPresent()) {
            return existed.get();
        }

        SeatLock lock = loadValidHoldOrThrow(holdId, userId);

        Ticket b = new Ticket();
        b.setUserId(userId);
        b.setShowtimeId(lock.getShowtimeId());
        b.setSeats(new ArrayList<>(lock.getSeats()));
        Long lockAmount = lock.getAmount();
        b.setAmount(lockAmount == null ? 0L : lockAmount);
        b.setStatus("PENDING_PAYMENT");
        b.setHoldId(holdId);
        b.setCreatedAt(Instant.now());

        Ticket.PaymentInfo p = b.getPayment();
        if (p == null) p = new Ticket.PaymentInfo();
        p.setGateway("ZALOPAY");
        if (p.getRaw() == null) p.setRaw(new HashMap<>());
        b.setPayment(p);

        try {
            b = ticketRepo.save(b);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ticketRepo.findByHoldId(holdId).orElseThrow(() -> e);
        }
        return b;
    }

    // ================== Finalize: CONFIRMED + ledger + cleanup hold ==================
    private Ticket finalizeConfirm(String bookingId, Instant paidAt, String transId, Map<String, ?> extraRaw) {
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) return b; // idempotent

        // --- cập nhật trạng thái + payment (đúng kiểu POJO PaymentInfo của bạn) ---
        b.setStatus("CONFIRMED");
        Ticket.PaymentInfo p = (b.getPayment() != null) ? b.getPayment() : new Ticket.PaymentInfo();
        if (p.getRaw() == null) p.setRaw(new HashMap<>());
        p.setPaidAt(paidAt == null ? Instant.now() : paidAt);
        if (transId != null) p.setZpTransId(transId);
        if (extraRaw != null) p.getRaw().putAll(extraRaw);
        b.setPayment(p);

        ticketRepo.save(b);

        // --- ✅ confirm seats trong ledger: truyền đủ 4 tham số (showtimeId, seats, bookingId, holdId) ---
        try {
            String holdId = b.getHoldId(); // Ticket của bạn luôn có holdId (đã @Indexed unique)
            List<String> seats = b.getSeats();
            String showtimeId = b.getShowtimeId();
            if (holdId == null || seats == null || seats.isEmpty() || showtimeId == null) {
                // Log & bỏ qua để không NPE
                log.warn("Skip confirmMany due to missing fields. holdId={}, seats={}, showtimeId={}",
                        holdId, seats, showtimeId);
            } else {
                long updated = ledgerRepo.confirmMany(showtimeId, seats, b.getId(), holdId);
                log.debug("ledger confirmMany updated={}", updated);
            }
        } catch (Exception e) {
            log.warn("ledger confirm failed: {}", e.toString());
        }

        // --- dọn SeatLock sau khi confirm ---
        try {
            if (b.getHoldId() != null) lockRepo.deleteById(b.getHoldId());
        } catch (Exception ignore) {}

        return b;
    }
}