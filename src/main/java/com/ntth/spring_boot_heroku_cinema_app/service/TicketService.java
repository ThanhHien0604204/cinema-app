package com.ntth.spring_boot_heroku_cinema_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ThreadLocalRandom;

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
    @Transactional
    public Ticket confirmBookingFromPending(String bookingId, String userId) {
        log.info("Starting confirm process for bookingId={}, userId={}", bookingId, userId);

        // 1. Find và validate booking
        Ticket b = ticketRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        // Check ownership
        if (!Objects.equals(b.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_YOUR_BOOKING");
        }

        // Check current status
        if (!"PENDING_PAYMENT".equalsIgnoreCase(b.getStatus())) {
            if ("CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                log.info("Booking {} already confirmed", bookingId);
                return b;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_STATUS: " + b.getStatus());
        }

        // 2. Check payment đã complete chưa
        Ticket.PaymentInfo pay = b.getPayment();
        if (pay == null || pay.getTxId() == null) { // Dùng txId thay vì status vì Ticket không có payment.status
            log.warn("Payment not completed for booking {}: txId={}", bookingId, pay != null ? pay.getTxId() : "null");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_COMPLETED");
        }

        // 3. Get seats và holdId
        List<String> seats = b.getSeats();
        String holdId = b.getHoldId();
        String showtimeId = b.getShowtimeId();

        if (seats == null || seats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_SEATS_IN_BOOKING");
        }
        if (showtimeId == null || showtimeId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_SHOWTIME_ID");
        }

        // 4. UPDATE TICKET STATUS = CONFIRMED
        b.setStatus("CONFIRMED");
        if (pay.getPaidAt() == null) {
            pay.setPaidAt(Instant.now());
        }
        ticketRepo.save(b);
        log.info("Updated ticket status to CONFIRMED: {}", bookingId);

        // 5. UPDATE SEAT_LEDGER: status=CONFIRMED, refType=BOOKING, refId=ticketId
        try {
            long updatedSeats = updateSeatsToConfirmed(showtimeId, seats, bookingId);

            if (updatedSeats != seats.size()) {
                log.error("Seat update failed: updated={} expected={} for booking={}",
                        updatedSeats, seats.size(), bookingId);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        String.format("SEAT_UPDATE_FAILED: %d/%d seats confirmed", updatedSeats, seats.size()));
            }
            log.info("Updated {} seats to CONFIRMED for booking={}", updatedSeats, bookingId);
        } catch (Exception e) {
            log.error("Failed to update seats for booking={}: {}", bookingId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SEAT_UPDATE_ERROR");
        }

        // 6. DELETE SEAT_LOCK
        try {
            if (holdId != null && !holdId.isBlank()) {
                lockRepo.deleteById(holdId);
                log.info("Deleted seat lock: holdId={}", holdId);
            } else {
                log.warn("No holdId found for booking={}", bookingId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete seat lock for booking={}: {}", bookingId, e.getMessage(), e);
            // Không throw error vì đã CONFIRMED ticket và seats
        }

        log.info("Booking CONFIRMED successfully: bookingId={}, seats={}, userId={}",
                bookingId, seats.size(), userId);
        return b;
    }

    /**
     * Update seats từ HOLD → CONFIRMED using MongoTemplate
     */
    private long updateSeatsToConfirmed(String showtimeId, List<String> seats, String ticketId) {
        // Build query: find seats with status HOLD và refType LOCK
        Query query = new Query();
        query.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        query.addCriteria(Criteria.where("seatNumber").in(seats));
        query.addCriteria(Criteria.where("status").is("HOLD"));
        // THÊM: Check refType = LOCK (nếu SeatLedger có field này)
        // Nếu không có refType, bỏ dòng này
        query.addCriteria(Criteria.where("refType").is("LOCK"));

        // Count seats cần update để validate
        long totalSeats = mongo.count(query, SeatLedger.class);
        if (totalSeats == 0) {
            log.warn("No HOLD seats found to confirm for showtimeId={}, seats={}", showtimeId, seats);
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
        long updated = mongo.updateMulti(query, update, SeatLedger.class).getModifiedCount();

        log.debug("Updated {} seats to CONFIRMED (expected: {}) for ticket={}", updated, totalSeats, ticketId);
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
    public long freeConfirmedSeats(String showtimeId, List<String> seats, String bookingId) {
        // Build query cho confirmed seats
        Query query = new Query();
        query.addCriteria(Criteria.where("showtimeId").is(showtimeId));
        query.addCriteria(Criteria.where("seatNumber").in(seats));
        query.addCriteria(Criteria.where("status").is("CONFIRMED"));
        query.addCriteria(Criteria.where("refType").is("BOOKING"));
        query.addCriteria(Criteria.where("refId").is(bookingId));

        // Count seats để validate
        long totalSeats = mongo.count(query, SeatLedger.class);

        // Build update để free seats
        Update update = new Update();
        update.set("status", "FREE");
        update.set("refType", null);
        update.set("refId", null);
        update.unset("expiresAt");

        // FIX: updateMulti với đúng signature
        long updated = mongo.updateMulti(query, update, SeatLedger.class).getModifiedCount();

        log.info("Freed {} confirmed seats for booking={}", updated, bookingId);
        return updated;
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
            long confirmed = freeConfirmedSeats(hold.getShowtimeId(), hold.getSeats(), booking.getId());
            if (confirmed != hold.getSeats().size()) {
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
        return createBookingFromHold(holdId, userId, "ZALOPAY");
    }

    /**
     * Xử lý IPN từ ZaloPay
     * @param req request map
     */
    @Transactional
    public void handleZpIpn(Map<String, String> req) {
        String data = req.get("data");
        String mac  = req.get("mac");
        if (data == null || mac == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_DATA_OR_MAC");
        }

        Map<String, Object> verify = zalo.verifyIpn(data, mac);
        int returnCode = (int) verify.getOrDefault("return_code", -1);
        if (returnCode != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IPN");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) verify.get("parsed");
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARSE_FAILED");
        }

        String appTransId = String.valueOf(parsed.get("app_trans_id"));
        if (appTransId == null || appTransId.isEmpty() || !appTransId.contains("_")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_APP_TRANS_ID");
        }
        String bookingCode = appTransId.split("_", 2)[1];

        Ticket b = ticketRepo.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND"));

        Ticket.PaymentInfo pay = b.getPayment();
        if (pay == null) {
            pay = new Ticket.PaymentInfo();
            pay.setGateway("ZALOPAY");
            b.setPayment(pay);
        }

        // Ghi zp_trans_id/txId
        Object zptObj = parsed.get("zp_trans_id");
        if (zptObj != null) {
            String zpt = String.valueOf(zptObj);
            pay.setTxId(zpt);
            try { pay.setZpTransId(zpt); } catch (Throwable ignore) {}
        }
        pay.setRaw(parsed);

        // Đọc status/amount từ IPN
        int statusFromZp = safeInt(parsed.get("status"), -1);
        long amountFromZp = 0L;
        Object amountObj = parsed.get("amount");
        if (amountObj instanceof Number) {
            amountFromZp = ((Number) amountObj).longValue();
        } else if (amountObj != null) {
            try { amountFromZp = Long.parseLong(String.valueOf(amountObj)); } catch (Exception ignore) {}
        }

        // Check amount an toàn
        Long bookingAmount = b.getAmount();
        if (bookingAmount != null && amountFromZp > 0 && bookingAmount.longValue() != amountFromZp) {
            log.warn("IPN amount mismatch booking={} zp={}", bookingAmount, amountFromZp);
            try {
                b.setStatus("FAILED");
                ticketRepo.save(b);
            } catch (Throwable ignore) {}
            return;
        }

        // THAY ĐỔI LOGIC: status=0 hoặc 1 đều CONFIRMED ngay
        if (statusFromZp == 0 || statusFromZp == 1) {
            // CẢ PENDING VÀ SUCCESS ĐỀU CONFIRMED NGAY LẬP TỨC
            if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
                b.setStatus("CONFIRMED");
                pay.setPaidAt(Instant.now());
                ticketRepo.save(b);

                // Confirm ghế trong ledger
                long updated = ledgerRepo.confirmMany(b.getShowtimeId(), b.getSeats(), b.getId(), b.getHoldId());
                if (updated != (b.getSeats() == null ? 0 : b.getSeats().size())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_TAKEN_OR_HOLD_MISMATCH");
                }

                // Xóa hold
                try { lockRepo.deleteById(b.getHoldId()); } catch (Throwable ignore) {}

                log.info("IPN CONFIRMED: bookingId={}, statusFromZp={}, holdId={}, seats={}",
                        b.getId(), statusFromZp, b.getHoldId(), b.getSeats());
            }
            return;
        }

        // Các status còn lại (2, 3, ...) coi như fail
        if (!"CONFIRMED".equalsIgnoreCase(b.getStatus())) {
            b.setStatus("CANCELED");
            ticketRepo.save(b);
        }
        log.debug("IPN not success (status={}) booking={} -> CANCELED", statusFromZp, bookingCode);
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
}